/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.handler.root.resources.endpoint.mfa;

import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.manager.form.FormManager;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.FactorNotFoundException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.gravitee.common.util.Maps;
import io.gravitee.gateway.api.el.EvaluableRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

import static io.gravitee.am.common.factor.FactorSecurityType.SHARED_SECRET;
import static io.gravitee.am.gateway.handler.common.utils.RoutingContextHelper.getEvaluableAttributes;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAChallengeEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(MFAChallengeEndpoint.class);

    private final FactorManager factorManager;
    private final UserService userService;
    private final ApplicationContext applicationContext;

    public MFAChallengeEndpoint(FactorManager factorManager, UserService userService, TemplateEngine engine, ApplicationContext applicationContext) {
        super(engine);
        this.applicationContext = applicationContext;
        this.factorManager = factorManager;
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "GET":
                renderMFAPage(routingContext);
                break;
            case "POST":
                verifyCode(routingContext);
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void renderMFAPage(RoutingContext routingContext) {
        try {
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            if (routingContext.user() == null) {
                logger.warn("User must be authenticated to request MFA challenge.");
                routingContext.fail(401);
                return;
            }
            final io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            final Factor factor = getFactor(routingContext, client, endUser);
            final String error = routingContext.request().getParam(ConstantKeys.ERROR_PARAM_KEY);

            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            final String action = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true);
            routingContext.put(ConstantKeys.FACTOR_KEY, factor);
            routingContext.put(ConstantKeys.ACTION_KEY, action);
            routingContext.put(ConstantKeys.ERROR_PARAM_KEY, error);

            final FactorProvider factorProvider = factorManager.get(factor.getId());

            // send challenge
            sendChallenge(factorProvider, routingContext, factor, endUser, resChallenge -> {
                if (resChallenge.failed()) {
                    logger.error("An error has occurred when sending MFA challenge", resChallenge.cause());
                    routingContext.fail(503);
                    return;
                }
                // render the mfa challenge page
                this.renderPage(routingContext, routingContext.data(), client, logger, "Unable to render MFA challenge page");
            });
        } catch (Exception ex) {
            logger.error("An error has occurred when rendering MFA challenge page", ex);
            routingContext.fail(503);
        }
    }

    private void verifyCode(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        if (routingContext.user() == null) {
            logger.warn("User must be authenticated to submit MFA challenge.");
            routingContext.fail(401);
            return;
        }
        io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
        final Factor factor = getFactor(routingContext, client, endUser);
        MultiMap params = routingContext.request().formAttributes();
        final String code = params.get("code");
        final String factorId = params.get("factorId");
        if (code == null) {
            logger.warn("No code in form - did you forget to include code value ?");
            routingContext.fail(400);
            return;
        }
        if (factorId == null) {
            logger.warn("No factor id in form - did you forget to include factor id value ?");
            routingContext.fail(400);
            return;
        }
        FactorProvider factorProvider = factorManager.get(factorId);
        EnrolledFactor enrolledFactor = getEnrolledFactor(routingContext, factor, endUser);
        Map<String, Object> factorData = new HashMap<>();
        factorData.putAll(getEvaluableAttributes(routingContext));
        factorData.put(FactorContext.KEY_ENROLLED_FACTOR, enrolledFactor);
        factorData.put(FactorContext.KEY_CODE, code);
        final FactorContext factorCtx = new FactorContext(applicationContext, factorData);

        verify(factorProvider, factorCtx, h -> {
            if (h.failed()) {
                handleException(routingContext);
                return;
            }
            // save enrolled factor if needed and redirect to the original url
            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            final String returnURL = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/oauth/authorize", queryParams, true);
            routingContext.session().put(ConstantKeys.MFA_FACTOR_ID_CONTEXT_KEY, factorId);

            if (routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_ID_KEY) != null || factorProvider.useVariableFactorSecurity()) {
                enrolledFactor.setStatus(FactorStatus.ACTIVATED);
                saveFactor(endUser, factorProvider.changeVariableFactorSecurity(enrolledFactor), fh -> {
                    if (fh.failed()) {
                        logger.error("An error occurs while saving enrolled factor for the current user", fh.cause());
                        handleException(routingContext);
                        return;
                    }

                    // clean session
                    routingContext.session().remove(ConstantKeys.ENROLLED_FACTOR_ID_KEY);
                    routingContext.session().remove(ConstantKeys.ENROLLED_FACTOR_SECURITY_VALUE_KEY);
                    routingContext.session().remove(ConstantKeys.ENROLLED_FACTOR_PHONE_NUMBER);
                    routingContext.session().remove(ConstantKeys.ENROLLED_FACTOR_EMAIL_ADDRESS);

                    // update user strong auth status
                    routingContext.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
                    routingContext.session().put(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY, true);
                    doRedirect(routingContext.request().response(), returnURL);
                });
            } else {
                // update user strong auth status
                routingContext.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
                routingContext.session().put(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY, true);
                doRedirect(routingContext.request().response(), returnURL);
            }
        });
    }

    private void verify(FactorProvider factorProvider, FactorContext factorContext, Handler<AsyncResult<Void>> handler) {
        factorProvider.verify(factorContext)
                .subscribe(
                        () -> handler.handle(Future.succeededFuture()),
                        error -> handler.handle(Future.failedFuture(error)));
    }

    private void saveFactor(User user, Single<EnrolledFactor> enrolledFactor, Handler<AsyncResult<User>> handler) {
        enrolledFactor.flatMap(factor -> userService.addFactor(user.getId(), factor, new DefaultUser(user)))
                .subscribe(
                        user1 -> handler.handle(Future.succeededFuture(user1)),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }

    private void sendChallenge(FactorProvider factorProvider, RoutingContext routingContext, Factor factor, User endUser, Handler<AsyncResult<Void>> handler) {
        if (!factorProvider.needChallengeSending() || routingContext.get(ConstantKeys.ERROR_PARAM_KEY) != null) {
            // do not send challenge in case of error param to avoid useless code generation
            handler.handle(Future.succeededFuture());
            return;
        }

        EnrolledFactor enrolledFactor = getEnrolledFactor(routingContext, factor, endUser);
        Map<String, Object> factorData = new HashMap<>();
        factorData.putAll(getEvaluableAttributes(routingContext));
        factorData.put(FactorContext.KEY_CLIENT, routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY));
        factorData.put(FactorContext.KEY_USER, endUser);
        factorData.put(FactorContext.KEY_REQUEST, new EvaluableRequest(new VertxHttpServerRequest(routingContext.request().getDelegate())));
        factorData.put(FactorContext.KEY_ENROLLED_FACTOR, enrolledFactor);
        FactorContext factorContext = new FactorContext(applicationContext, factorData);

        factorProvider.sendChallenge(factorContext)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> handler.handle(Future.succeededFuture()),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }

    private Factor getFactor(RoutingContext routingContext, Client client, User endUser) {
        // factor can be either in session (if user come from mfa/enroll page)
        // or from the user enrolled factor list
        final String savedFactorId = routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_ID_KEY);
        if (savedFactorId != null) {
            return factorManager.getFactor(savedFactorId);
        }

        if (endUser.getFactors() == null) {
            throw new FactorNotFoundException("No factor found for the end user");
        }

        return endUser.getFactors()
                .stream()
                .filter(enrolledFactor -> client.getFactors().contains(enrolledFactor.getFactorId()))
                .findFirst()
                .map(enrolledFactor -> factorManager.getFactor(enrolledFactor.getFactorId()))
                .orElseThrow(() -> new FactorNotFoundException("No factor found for the end user"));
    }

    private EnrolledFactor getEnrolledFactor(RoutingContext routingContext, Factor factor, User endUser) {
        // enrolled factor can be either in session (if user come from mfa/enroll page)
        // or from the user enrolled factor list
        final String savedFactorId = routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_ID_KEY);
        if (factor.getId().equals(savedFactorId)) {
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(factor.getId());
            switch (factor.getFactorType()) {
                case OTP:
                    enrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET,
                            routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_SECURITY_VALUE_KEY)));
                    break;
                case SMS:
                    enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS,
                            routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_PHONE_NUMBER)));
                    break;
                case EMAIL:
                    Map<String, Object> additionalData = new Maps.MapBuilder(new HashMap())
                            .put(FactorDataKeys.KEY_MOVING_FACTOR, generateInitialMovingFactor(endUser))
                            .build();
                    // For email even if the endUser will contains all relevant information, we extract only the Expiration Date of the code.
                    // this is done only to enforce the other parameter (shared secret and initialMovingFactor)
                    getEnrolledFactor(factor, endUser).ifPresent(ef -> {
                        additionalData.put(FactorDataKeys.KEY_EXPIRE_AT, ef.getSecurity().getData(FactorDataKeys.KEY_EXPIRE_AT, Long.class));
                    });
                    enrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET,
                            routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_SECURITY_VALUE_KEY),
                            additionalData));
                    enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL,
                            routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_EMAIL_ADDRESS)));
                    break;
            }
            enrolledFactor.setCreatedAt(new Date());
            enrolledFactor.setUpdatedAt(enrolledFactor.getCreatedAt());
            return enrolledFactor;
        }

        return getEnrolledFactor(factor, endUser)
                .orElseThrow(() -> new FactorNotFoundException("No enrolled factor found for the end user"));
    }

    private Optional<EnrolledFactor> getEnrolledFactor(Factor factor, User endUser) {
        if (endUser.getFactors() == null) {
            return Optional.empty();
        }

        return endUser.getFactors()
                .stream()
                .filter(f -> factor.getId().equals(f.getFactorId()))
                .findFirst();
    }

    private int generateInitialMovingFactor(User endUser) {
        try {
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.setSeed(endUser.getUsername().getBytes(StandardCharsets.UTF_8));
            return secureRandom.nextInt(1000) + 1;
        } catch (NoSuchAlgorithmException e) {
            return 0;
        }
    }

    @Override
    public String getTemplateSuffix() {
        return Template.MFA_CHALLENGE.template();
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    private void handleException(RoutingContext context) {
        final HttpServerRequest req = context.request();
        final HttpServerResponse resp = context.response();

        // redirect to mfa challenge page with error message
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(req.uri());
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.putAll(queryStringDecoder.parameters().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0))));
        parameters.put("error", "mfa_challenge_failed");
        String uri = UriBuilderRequest.resolveProxyRequest(req, req.path(), parameters, true);
        doRedirect(resp, uri);
    }
}
