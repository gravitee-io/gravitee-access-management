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
package io.gravitee.am.gateway.handler.oauth2;

import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.api.AbstractProtocolProvider;
import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.common.service.AuthenticationFlowContextService;
import io.gravitee.am.gateway.handler.common.service.ratelimit.DeviceFlowRateLimiterService;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.AuthenticationFlowContextHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.AuthenticationFlowHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.CSPHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.PolicyChainHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.SSOSessionHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.XFrameHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.XSSHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieSessionHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.ClientAuthHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device.DeviceAuthorizationEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device.DeviceFlowPageRenderer;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device.DeviceVerificationEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device.DeviceVerificationSubmitEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device.DeviceVerificationUriResolver;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseClientHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.device.DeviceVerificationReturnUrlHandler;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.root.resources.handler.LocaleHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.i18n.GraviteeMessageResolver;
import io.gravitee.am.service.impl.SecretService;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.CSRFHandler;
import io.vertx.rxjava3.ext.web.handler.CorsHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

public class DeviceFlowProvider extends AbstractProtocolProvider {

    public static final String PATH_DEVICE_AUTHORIZATION = "/device_authorization";
    public static final String PATH_DEVICE = "/device";

    @Autowired
    private Domain domain;

    @Autowired
    private Router router;

    @Autowired
    private Vertx vertx;

    @Autowired
    @Qualifier("regularClientLookupService")
    private ClientLookupService regularClientLookupService;

    @Autowired
    @Qualifier("complexClientLookupService")
    private ClientLookupService complexClientLookupService;

    @Autowired
    private ClientAssertionService clientAssertionService;

    @Autowired
    private JWKService jwkService;

    @Autowired
    private SecretService secretService;

    @Autowired
    private DeviceFlowPageRenderer pageRenderer;

    @Autowired
    private CookieSessionHandler sessionHandler;

    @Autowired
    private SSOSessionHandler ssoSessionHandler;

    @Autowired
    private CSRFHandler csrfHandler;

    @Autowired
    private CSPHandler cspHandler;

    @Autowired
    private XFrameHandler xframeHandler;

    @Autowired
    private XSSHandler xssHandler;

    @Autowired
    private PolicyChainHandler policyChainHandler;

    @Autowired
    private CorsHandler corsHandler;

    @Autowired
    private AuthenticationFlowHandler authenticationFlowHandler;

    @Autowired
    private AuthenticationFlowContextService authenticationFlowContextService;

    @Autowired
    @Qualifier("gwMessageResolver")
    private GraviteeMessageResolver messageResolver;

    @Autowired
    private DeviceAuthorizationRequestService deviceAuthorizationRequestService;

    @Autowired
    private DeviceFlowRateLimiterService deviceFlowRateLimiterService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private Environment environment;

    @Value("${legacy.handler.globalRootFlow.enabled:false}")
    private boolean globalRootFlow = false;

    @Override
    public String path() {
        return "/oauth";
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (domain.useDeviceFlow()) {
            initRouter();
        }
    }

    private void initRouter() {
        final Router deviceRouter = Router.router(vertx);

        final String certificateHeader = environment.getProperty(ConstantKeys.HTTP_SSL_CERTIFICATE_HEADER);
        final Handler<RoutingContext> clientAuthHandler = ClientAuthHandler.create(complexClientLookupService, clientAssertionService, jwkService, domain, secretService, certificateHeader, auditService);

        deviceRouter.route(PATH_DEVICE)
                .handler(sessionHandler)
                .handler(ssoSessionHandler);
        deviceRouter.route(PATH_DEVICE).handler(csrfHandler);
        deviceRouter.route(PATH_DEVICE).handler(cspHandler);
        deviceRouter.route(PATH_DEVICE).handler(xframeHandler);
        deviceRouter.route(PATH_DEVICE).handler(xssHandler);
        deviceRouter.route(PATH_DEVICE).handler(new AuthenticationFlowContextHandler(authenticationFlowContextService, environment));

        applyRootExtensionPoint(deviceRouter);

        deviceRouter.route(HttpMethod.OPTIONS, PATH_DEVICE_AUTHORIZATION)
                .handler(corsHandler);
        deviceRouter.route(HttpMethod.POST, PATH_DEVICE_AUTHORIZATION)
                .handler(corsHandler)
                .handler(clientAuthHandler)
                .handler(new DeviceAuthorizationEndpoint(deviceAuthorizationRequestService, new DeviceVerificationUriResolver()));

        deviceRouter.route(HttpMethod.GET, PATH_DEVICE)
                .handler(new AuthorizationRequestParseClientHandler(regularClientLookupService))
                .handler(new DeviceVerificationReturnUrlHandler())
                .handler(authenticationFlowHandler.create())
                .handler(new LocaleHandler(messageResolver))
                .handler(new DeviceVerificationEndpoint(pageRenderer));

        deviceRouter.route(HttpMethod.POST, PATH_DEVICE)
                .handler(new AuthorizationRequestParseClientHandler(regularClientLookupService))
                .handler(authenticationFlowHandler.create())
                .handler(new DeviceVerificationSubmitEndpoint(deviceAuthorizationRequestService, pageRenderer, deviceFlowRateLimiterService, auditService));

        deviceRouter.route().failureHandler(new ExceptionHandler());

        router.route(subRouterPath()).subRouter(deviceRouter);
    }

    private void applyRootExtensionPoint(Router deviceRouter) {
        if (!globalRootFlow) {
            final var rootExtensionPointHandler = policyChainHandler.create(ExtensionPoint.ROOT);
            deviceRouter.route(PATH_DEVICE).handler(rootExtensionPointHandler);
            deviceRouter.route(PATH_DEVICE_AUTHORIZATION).handler(rootExtensionPointHandler);
        }
    }
}
