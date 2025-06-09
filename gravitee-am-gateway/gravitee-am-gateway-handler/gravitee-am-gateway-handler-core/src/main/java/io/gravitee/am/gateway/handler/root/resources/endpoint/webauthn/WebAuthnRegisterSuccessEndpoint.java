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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.handler.webauthn.WebAuthnHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DomainDataPlane;
import io.gravitee.am.service.exception.CredentialNotFoundException;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnRegisterSuccessEndpoint extends WebAuthnHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnRegisterSuccessEndpoint.class);

    public WebAuthnRegisterSuccessEndpoint(TemplateEngine templateEngine,
                                           CredentialGatewayService credentialService,
                                           DomainDataPlane domainDataPlane) {
        super(templateEngine);
        setCredentialService(credentialService);
        setDomainDataplane(domainDataPlane);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "GET":
                render(routingContext);
                break;
            case "POST":
                save(routingContext);
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void render(RoutingContext routingContext) {
        try {
            // control that a WebAuthn Credential has been registered
            final String credentialId = routingContext.session().get(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY);
            if (credentialId == null) {
                logger.error("No WebAuthn credential has been registered");
                routingContext.fail(400);
                return;
            }
            // get the current user
            final User authenticatedUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            // get the current application
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            // prepare the context
            final Map<String, Object> data = generateData(routingContext, domainDataPlane.getDomain(), client);
            // add the device name
            final String deviceName = authenticatedUser.getUsername() + "'s " + getClientOS(routingContext.request());
            data.put(ConstantKeys.PASSWORDLESS_DEVICE_NAME, deviceName);
            // add the action form
            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            final String action = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true);
            data.put(ConstantKeys.ACTION_KEY, action);
            // render the page
            renderPage(routingContext, data, client, logger, "Unable to render WebAuthn register success page");
        } catch (Exception ex) {
            logger.error("An error has occurred when rendering WebAuthn register success page", ex);
            routingContext.fail(503);
        }
    }

    private void save(RoutingContext routingContext) {
        final String deviceName = routingContext.request().getParam(ConstantKeys.PASSWORDLESS_DEVICE_NAME);
        final String credentialId = routingContext.session().get(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY);
        if (credentialId == null) {
            logger.error("No WebAuthn credential has been registered");
            routingContext.fail(400);
            return;
        }
        if (StringUtils.isBlank(deviceName)) {
            logger.debug("Request missing deviceName field");
            routingContext.fail(400);
            return;
        }

        if (deviceName.length() > 64) {
            logger.debug("deviceName must be below 64 characters");
            routingContext.fail(400);
            return;
        }

        credentialService.findByCredentialId(domainDataPlane.getDomain(), credentialId)
                .firstElement()
                .switchIfEmpty(Single.error(new CredentialNotFoundException(credentialId)))
                .flatMap(credential -> {
                    credential.setDeviceName(deviceName);
                    credential.setUpdatedAt(new Date());
                    return credentialService.update(domainDataPlane.getDomain(), credential);
                })
                .subscribe(__ -> {
                            // at this stage the registration has been done
                            // redirect the user to the original request
                            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
                            final String redirectUri = getReturnUrl(routingContext, queryParams);
                            routingContext.response()
                                    .putHeader(HttpHeaders.LOCATION, redirectUri)
                                    .setStatusCode(302)
                                    .end();
                        },
                        error -> {
                            logger.error("An error has occurred when updating the webauthn credential {}", credentialId, error);
                            routingContext.fail(error);
                        });
    }

    @Override
    public String getTemplateSuffix() {
        return Template.WEBAUTHN_REGISTER_SUCCESS.template();
    }

    private static String getClientOS(HttpServerRequest request) {
        final String userAgent = Optional.ofNullable(request.getHeader(HttpHeaders.USER_AGENT)).orElse("Unknown");
        return DeviceBrowser.getFromUserAgent(userAgent).capitalize();
    }

    public enum DeviceBrowser {
        ANDROID("android"),
        IPHONE("iphone"),
        MAC("mac"),
        UNIX("x11"),
        WINDOWS("windows"),
        DEVICE("device");

        private final String device;

        DeviceBrowser(String device) {
            this.device = device;
        }

        public static DeviceBrowser getFromUserAgent(String userAgent) {
            var lowerCaseUserAgent = userAgent.toLowerCase();
            return Arrays.stream(DeviceBrowser.values()).filter(deviceBrowser ->
                    lowerCaseUserAgent.contains(deviceBrowser.device)
            ).findFirst().orElse(DEVICE);
        }

        public String capitalize() {
            return IPHONE.equals(this) ? "iPhone" : StringUtils.capitalize(name().toLowerCase());
        }
    }
}
