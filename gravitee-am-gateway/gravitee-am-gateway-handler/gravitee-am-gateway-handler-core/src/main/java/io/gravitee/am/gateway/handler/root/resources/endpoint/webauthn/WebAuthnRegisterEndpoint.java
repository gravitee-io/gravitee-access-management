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
package io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.handler.webauthn.WebAuthnHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.am.service.FactorService;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnRegisterEndpoint extends WebAuthnHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnRegisterEndpoint.class);
    private static final String SKIP_WEBAUTHN_PARAM_KEY = "skipWebAuthN";
    private final Domain domain;

    public WebAuthnRegisterEndpoint(TemplateEngine templateEngine,
                                    Domain domain) {
        super(templateEngine);
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        renderPage(routingContext);
    }

    private void renderPage(RoutingContext routingContext) {
        try {
            // session validation
            if (routingContext.session() == null) {
                logger.warn("No session or session handler is missing.");
                routingContext.fail(500);
                return;
            }

            if (routingContext.user() == null) {
                logger.warn("User must be authenticated to register WebAuthn credentials.");
                routingContext.fail(401);
                return;
            }

            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());

            // check if user has skipped this step
            final HttpServerRequest request = routingContext.request();
            if (Boolean.parseBoolean(request.getParam(SKIP_WEBAUTHN_PARAM_KEY))) {
                queryParams.remove(SKIP_WEBAUTHN_PARAM_KEY);
                final String returnURL = getReturnUrl(routingContext, queryParams);
                routingContext.session().put(ConstantKeys.WEBAUTHN_SKIPPED_KEY, true);
                // Now redirect back to the original url
                doRedirect(routingContext.response(), returnURL);
                return;
            }

            // prepare the context
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            final User user = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            final UserProperties userProperties = new UserProperties(user);

            final String action = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true);
            final String skipAction = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams.set("skipWebAuthN", "true"), true);
            if(isEnrollingFido2Factor(routingContext)){
                routingContext.put(ConstantKeys.MFA_ENROLLING_FIDO2_FACTOR, "true");
            }
            routingContext.put(ConstantKeys.ACTION_KEY, action);
            routingContext.put(ConstantKeys.SKIP_ACTION_KEY, skipAction);
            routingContext.put(ConstantKeys.USER_CONTEXT_KEY, userProperties);
            routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, Collections.singletonMap(Parameters.CLIENT_ID, client.getClientId()));

            if (domain.getWebAuthnSettings() != null && domain.getWebAuthnSettings().getAuthenticatorAttachment() != null) {
                routingContext.put(ConstantKeys.PARAM_AUTHENTICATOR_ATTACHMENT_KEY, domain.getWebAuthnSettings().getAuthenticatorAttachment().getValue());
            }

            // render the webauthn register page
            this.renderPage(routingContext, generateData(routingContext, domain, client), client, logger, "Unable to render WebAuthn register page");
        } catch (Exception ex) {
            logger.error("An error has occurred while rendering WebAuthn register page", ex);
            routingContext.fail(503);
        }
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    @Override
    public String getTemplateSuffix() {
        return Template.WEBAUTHN_REGISTER.template();
    }

}
