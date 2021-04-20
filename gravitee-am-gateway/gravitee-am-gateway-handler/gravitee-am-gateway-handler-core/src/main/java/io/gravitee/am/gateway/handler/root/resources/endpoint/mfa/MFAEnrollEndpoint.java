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
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.factor.FactorManager;
import io.gravitee.am.gateway.handler.form.FormManager;
import io.gravitee.am.gateway.handler.root.resources.auth.handler.FormLoginHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.Observable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAEnrollEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(MFAEnrollEndpoint.class);
    private static final String CLIENT_CONTEXT_KEY = "client";
    private static final String MFA_SKIPPED_KEY = "mfaEnrollmentSkipped";
    private static final String ENROLLED_FACTOR_KEY = "enrolledFactor";
    private Domain domain;
    private FactorManager factorManager;
    private ThymeleafTemplateEngine engine;

    public MFAEnrollEndpoint(FactorManager factorManager, ThymeleafTemplateEngine engine) {
        this.factorManager = factorManager;
        this.engine = engine;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method()) {
            case GET:
                renderPage(routingContext);
                break;
            case POST:
                saveEnrollment(routingContext);
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void renderPage(RoutingContext routingContext) {
        try {
            final io.gravitee.am.model.User endUser =
                ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            final Client client = routingContext.get(CLIENT_CONTEXT_KEY);
            final Map<io.gravitee.am.model.Factor, FactorProvider> factors = getFactors(client);
            final String action = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().uri(), null);

            // load factor providers
            load(
                factors,
                endUser,
                h -> {
                    if (h.failed()) {
                        logger.error("An error occurs while loading factor providers", h.cause());
                        routingContext.fail(503);
                        return;
                    }
                    // put factors in context
                    routingContext.put("factors", h.result());
                    routingContext.put("action", action);
                    // render the mfa enroll page
                    engine.render(
                        routingContext.data(),
                        getTemplateFileName(client),
                        res -> {
                            if (res.succeeded()) {
                                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                                routingContext.response().end(res.result());
                            } else {
                                logger.error("Unable to render MFA enroll page", res.cause());
                                routingContext.fail(res.cause());
                            }
                        }
                    );
                }
            );
        } catch (Exception ex) {
            logger.error("An error occurs while rendering MFA enroll page", ex);
            routingContext.fail(503);
        }
    }

    private void saveEnrollment(RoutingContext routingContext) {
        MultiMap params = routingContext.request().formAttributes();
        final Boolean acceptEnrollment = Boolean.valueOf(params.get("user_mfa_enrollment"));
        final String factorId = params.get("factorId");
        final String sharedSecret = params.get("sharedSecret");
        if (factorId == null) {
            logger.warn("No factor id in form - did you forget to include factor id value ?");
            routingContext.fail(400);
            return;
        }
        if (sharedSecret == null) {
            logger.warn("No shared secret in form - did you forget to include shared secret value ?");
            routingContext.fail(400);
            return;
        }
        // manage enrolled factors
        // if user has skipped the enrollment process, continue
        final String returnURL = routingContext.session().get(FormLoginHandler.DEFAULT_RETURN_URL_PARAM);
        if (!acceptEnrollment) {
            routingContext.session().put(MFA_SKIPPED_KEY, true);
            doRedirect(routingContext.response(), returnURL);
            return;
        }
        // save enrolled factor for the current user and continue
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId(factorId);
        enrolledFactor.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, sharedSecret));
        enrolledFactor.setCreatedAt(new Date());
        enrolledFactor.setUpdatedAt(enrolledFactor.getCreatedAt());
        routingContext.session().put(ENROLLED_FACTOR_KEY, enrolledFactor);
        doRedirect(routingContext.response(), returnURL);
    }

    private void load(Map<io.gravitee.am.model.Factor, FactorProvider> providers, User user, Handler<AsyncResult<List<Factor>>> handler) {
        Observable
            .fromIterable(providers.entrySet())
            .flatMapSingle(entry -> entry.getValue().enroll(user.getUsername()).map(enrollment -> new Factor(entry.getKey(), enrollment)))
            .toList()
            .subscribe(factors -> handler.handle(Future.succeededFuture(factors)), error -> handler.handle(Future.failedFuture(error)));
    }

    private Map<io.gravitee.am.model.Factor, FactorProvider> getFactors(Client client) {
        return client
            .getFactors()
            .stream()
            .filter(f -> factorManager.get(f) != null)
            .collect(Collectors.toMap(f -> factorManager.getFactor(f), f -> factorManager.get(f)));
    }

    private String getTemplateFileName(Client client) {
        return Template.MFA_ENROLL.template() + (client != null ? FormManager.TEMPLATE_NAME_SEPARATOR + client.getId() : "");
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    private class Factor {

        private String id;
        private String factorType;
        private Enrollment enrollment;

        public Factor(io.gravitee.am.model.Factor factor, Enrollment enrollment) {
            this.id = factor.getId();
            this.factorType = factor.getFactorType();
            this.enrollment = enrollment;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getFactorType() {
            return factorType;
        }

        public void setFactorType(String factorType) {
            this.factorType = factorType;
        }

        public Enrollment getEnrollment() {
            return enrollment;
        }

        public void setEnrollment(Enrollment enrollment) {
            this.enrollment = enrollment;
        }
    }
}
