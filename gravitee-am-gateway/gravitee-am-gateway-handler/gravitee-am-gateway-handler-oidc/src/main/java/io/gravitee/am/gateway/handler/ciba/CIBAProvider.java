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
package io.gravitee.am.gateway.handler.ciba;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.api.AbstractProtocolProvider;
import io.gravitee.am.gateway.handler.ciba.resources.handler.AuthenticationRequestAcknowledgeHandler;
import io.gravitee.am.gateway.handler.ciba.resources.handler.AuthenticationRequestCallbackHandler;
import io.gravitee.am.gateway.handler.ciba.resources.handler.AuthenticationRequestParametersHandler;
import io.gravitee.am.gateway.handler.ciba.resources.handler.AuthenticationRequestParseRequestObjectHandler;
import io.gravitee.am.gateway.handler.ciba.service.AuthenticationRequestService;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceSyncService;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.ClientAuthHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseProviderConfigurationHandler;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.impl.SecretService;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.CorsHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CIBAProvider extends AbstractProtocolProvider {

    public static final String CIBA_PATH = "/oidc/ciba";

    public static final String AUTHENTICATION_ENDPOINT = "/authenticate";
    public static final String AUTHENTICATION_CALLBACK_ENDPOINT = "/authenticate/callback";

    @Autowired
    private Domain domain;

    @Autowired
    private Router router;

    @Autowired
    private Vertx vertx;

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    private ProtectedResourceSyncService protectedResourceSyncService;

    @Autowired
    private ClientAssertionService clientAssertionService;

    @Autowired
    private JWKService jwkService;

    @Autowired
    private JWSService jwsService;

    @Autowired
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Autowired
    private RequestObjectService requestObjectService;

    @Autowired
    private AuthenticationRequestService authService;

    @Autowired
    private UserGatewayService userService;

    @Autowired
    private Environment environment;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private CorsHandler corsHandler;

    @Autowired
    private ScopeManager scopeManager;

    @Autowired
    private ProtectedResourceManager protectedResourceManager;

    @Autowired
    private SecretService secretService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private SubjectManager subjectManager;

    @Override
    public String path() {
        return CIBA_PATH;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (isCibaEnabled()) {
            initRouter();
        }
    }

    private void initRouter() {
        final Router cibaRouter = Router.router(vertx);

        final String certificateHeader = environment.getProperty(ConstantKeys.HTTP_SSL_CERTIFICATE_HEADER);
        final Handler<RoutingContext> clientAuthHandler = ClientAuthHandler.create(clientSyncService, clientAssertionService, jwkService, domain, secretService, certificateHeader, auditService, protectedResourceSyncService);

        cibaRouter.route(HttpMethod.OPTIONS, AUTHENTICATION_ENDPOINT)
                .handler(corsHandler);
        cibaRouter.route(HttpMethod.POST, AUTHENTICATION_ENDPOINT)
                .handler(corsHandler)
                .handler(clientAuthHandler)
                .handler(new AuthorizationRequestParseProviderConfigurationHandler(this.openIDDiscoveryService))
                .handler(new AuthenticationRequestParseRequestObjectHandler(this.requestObjectService))
                .handler(new AuthenticationRequestParametersHandler(domain, jwsService, jwkService, userService, scopeManager, subjectManager, protectedResourceManager))
                .handler(new AuthenticationRequestAcknowledgeHandler(authService, domain, jwtService));

        // To process the callback content we perform authentication of the caller that must be registered as AM client.
        // If a plugin need a non authenticate webhook, we should create another endpoint without clientAuthHandler.
        cibaRouter.route(HttpMethod.OPTIONS, AUTHENTICATION_CALLBACK_ENDPOINT)
                .handler(corsHandler);
        cibaRouter.route(HttpMethod.POST, AUTHENTICATION_CALLBACK_ENDPOINT)
                .handler(corsHandler)
                .handler(clientAuthHandler)
                .handler(new AuthenticationRequestCallbackHandler(authService));

        errorHandler(cibaRouter);

        router.mountSubRouter(path(), cibaRouter);
    }

    private void errorHandler(Router router) {
        router.route().failureHandler(new ExceptionHandler());
    }


    private boolean isCibaEnabled() {
        return domain.useCiba();
    }
}
