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

import io.gravitee.am.common.factor.FactorSecurityType;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.factor.FactorManager;
import io.gravitee.am.gateway.handler.form.FormManager;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.FactorNotFoundException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAChallengeEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(MFAChallengeEndpoint.class);

    private final FactorManager factorManager;
    private final UserService userService;
    private final ThymeleafTemplateEngine engine;

    public MFAChallengeEndpoint(FactorManager factorManager, UserService userService, ThymeleafTemplateEngine engine) {
        this.factorManager = factorManager;
        this.userService = userService;
        this.engine = engine;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method()) {
            case GET:
                renderMFAPage(routingContext);
                break;
            case POST:
                verifyCode(routingContext);
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void renderMFAPage(RoutingContext routingContext) {
        try {
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            final io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            final Factor factor = getFactor(routingContext, client, endUser);
            final String error = routingContext.request().getParam(ConstantKeys.ERROR_PARAM_KEY);

            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            final String action = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams);
            routingContext.put(ConstantKeys.FACTOR_KEY, factor);
            routingContext.put(ConstantKeys.ACTION_KEY, action);
            routingContext.put(ConstantKeys.ERROR_PARAM_KEY, error);

            // render the mfa challenge page
            engine.render(routingContext.data(), getTemplateFileName(client), res -> {
                if (res.succeeded()) {
                    routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                    routingContext.response().end(res.result());
                } else {
                    logger.error("Unable to render MFA challenge page", res.cause());
                    routingContext.fail(res.cause());
                }
            });
        } catch (Exception ex) {
            logger.error("An error occurs while rendering MFA challenge page", ex);
            routingContext.fail(503);
        }
    }

    private void verifyCode(RoutingContext routingContext) {
        io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
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
        EnrolledFactor enrolledFactor = getEnrolledFactor(routingContext, factorId, endUser);
        final String sharedSecret = enrolledFactor.getSecurity().getValue();
        verify(factorProvider, code, sharedSecret, h -> {
            if (h.failed()) {
                handleException(routingContext);
                return;
            }
            // save enrolled factor if needed and redirect to the original url
            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            final String returnURL = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/oauth/authorize", queryParams);
            routingContext.session().put(ConstantKeys.MFA_FACTOR_ID_CONTEXT_KEY, factorId);
            if (routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_ID_KEY) != null) {
                saveFactor(endUser.getId(), enrolledFactor, fh -> {
                    if (fh.failed()) {
                        logger.error("An error occurs while saving enrolled factor for the current user", fh.cause());
                        handleException(routingContext);
                        return;
                    }
                    // clean session
                    routingContext.session().remove(ConstantKeys.ENROLLED_FACTOR_ID_KEY);
                    routingContext.session().remove(ConstantKeys.ENROLLED_FACTOR_SECURITY_VALUE_KEY);

                    // update user strong auth status
                    routingContext.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
                    doRedirect(routingContext.request().response(), returnURL);
                });
            } else {
                // update user strong auth status
                routingContext.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
                doRedirect(routingContext.request().response(), returnURL);
            }
        });
    }

    private void verify(FactorProvider factorProvider, String code, String sharedSecret, Handler<AsyncResult<Void>> handler) {
        factorProvider.verify(sharedSecret, code)
                .subscribe(
                        () -> handler.handle(Future.succeededFuture()),
                        error -> handler.handle(Future.failedFuture(error)));
    }

    private void saveFactor(String userId, EnrolledFactor enrolledFactor, Handler<AsyncResult<User>> handler) {
        userService.addFactor(userId, enrolledFactor)
                .subscribe(
                        user -> handler.handle(Future.succeededFuture(user)),
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
        return endUser.getFactors()
                .stream()
                .filter(enrolledFactor -> client.getFactors().contains(enrolledFactor.getFactorId()))
                .findFirst()
                .map(enrolledFactor -> factorManager.getFactor(enrolledFactor.getFactorId()))
                .orElseThrow(() -> new FactorNotFoundException("No factor found for the end user"));
    }

    private EnrolledFactor getEnrolledFactor(RoutingContext routingContext, String factorId, User endUser) {
        // enrolled factor can be either in session (if user come from mfa/enroll page)
        // or from the user enrolled factor list
        final String savedFactorId = routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_ID_KEY);
        if (factorId.equals(savedFactorId)) {
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(factorId);
            enrolledFactor.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_SECURITY_VALUE_KEY)));
            enrolledFactor.setCreatedAt(new Date());
            enrolledFactor.setUpdatedAt(enrolledFactor.getCreatedAt());
            return enrolledFactor;
        }

        return endUser.getFactors()
                .stream()
                .filter(f -> factorId.equals(f.getFactorId()))
                .findFirst()
                .orElseThrow(() -> new FactorNotFoundException("No enrolled factor found for the end user"));
    }

    private String getTemplateFileName(Client client) {
        return Template.MFA_CHALLENGE.template() + (client != null ? FormManager.TEMPLATE_NAME_SEPARATOR + client.getId() : "");
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
        String uri = UriBuilderRequest.resolveProxyRequest(req, req.path(), parameters);
        doRedirect(resp, uri);
    }
}
