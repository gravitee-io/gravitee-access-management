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

import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.factor.FactorManager;
import io.gravitee.am.gateway.handler.form.FormManager;
import io.gravitee.am.gateway.handler.root.resources.auth.handler.FormLoginHandler;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
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

import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAChallengeEndpoint implements Handler<RoutingContext> {
    private static final Logger logger = LoggerFactory.getLogger(MFAChallengeEndpoint.class);
    private static final String CLIENT_CONTEXT_KEY = "client";
    private static final String STRONG_AUTH_COMPLETED  = "strongAuthCompleted";
    private static final String ENROLLED_FACTOR_KEY = "enrolledFactor";
    private static final String ERROR_PARAM = "error";
    private FactorManager factorManager;
    private UserService userService;
    private ThymeleafTemplateEngine engine;

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
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        final io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
        final Factor factor = getFactor(routingContext, client, endUser);
        final String error = routingContext.request().getParam(ERROR_PARAM);
        routingContext.put("factor", factor);
        routingContext.put("action", routingContext.request().uri());
        routingContext.put(ERROR_PARAM, error);

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
            final String returnURL = routingContext.session().get(FormLoginHandler.DEFAULT_RETURN_URL_PARAM);
            if (routingContext.session().get(ENROLLED_FACTOR_KEY) != null) {
                saveFactor(endUser.getId(), enrolledFactor, fh -> {
                    if (fh.failed()) {
                        logger.error("An error occurs while saving enrolled factor for the current user", fh.cause());
                        handleException(routingContext);
                        return;
                    }
                    // clean session
                    routingContext.session().remove(ENROLLED_FACTOR_KEY);
                    // update user strong auth status
                    routingContext.session().put(STRONG_AUTH_COMPLETED, true);
                    doRedirect(routingContext.request().response(), returnURL);
                });
            } else {
                // update user strong auth status
                routingContext.session().put(STRONG_AUTH_COMPLETED, true);
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
        if (routingContext.session().get(ENROLLED_FACTOR_KEY) != null) {
            EnrolledFactor enrolledFactor = routingContext.session().get(ENROLLED_FACTOR_KEY);
            return factorManager.getFactor(enrolledFactor.getFactorId());
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
        if (routingContext.session().get(ENROLLED_FACTOR_KEY) != null) {
            EnrolledFactor enrolledFactor = routingContext.session().get(ENROLLED_FACTOR_KEY);
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

        try {
            // redirect to mfa challenge page with error message
            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(req.uri());
            Map<String, String> parameters = new LinkedHashMap<>();
            parameters.putAll(queryStringDecoder.parameters().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0))));
            parameters.put("error", "mfa_challenge_failed");
            String uri = UriBuilderRequest.resolveProxyRequest(req, req.path(), parameters);
            doRedirect(resp, uri);
        } catch (URISyntaxException e) {
            context.fail(503);
        }
    }
}
