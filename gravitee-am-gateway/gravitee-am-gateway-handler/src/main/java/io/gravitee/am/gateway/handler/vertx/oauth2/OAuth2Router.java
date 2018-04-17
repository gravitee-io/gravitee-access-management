package io.gravitee.am.gateway.handler.vertx.oauth2;

import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.vertx.auth.handler.ClientBasicAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.handler.ClientCredentialsAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.handler.RedirectAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.provider.ClientAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.auth.provider.UserAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.oauth2.endpoint.AuthorizeEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.oauth2.endpoint.CheckTokenEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.oauth2.endpoint.TokenEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.oauth2.endpoint.UserApprovalEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.oauth2.endpoint.introspection.IntrospectionEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.oauth2.handler.AuthorizationRequestParseHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.auth.AuthProvider;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.*;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2Router {

    @Autowired
    private TokenGranter tokenGranter;

    @Autowired
    private ClientService clientService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private IntrospectionService introspectionService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private AuthorizationCodeService authorizationCodeService;

    @Autowired
    private UserAuthenticationManager userAuthenticationManager;

    @Autowired
    private Vertx vertx;

    @Autowired
    private Domain domain;

    public void route(Router router) {
        // create authentication handlers
        final AuthProvider clientAuthProvider = new AuthProvider(new ClientAuthenticationProvider(clientService));
        final AuthProvider userAuthProvider = new AuthProvider(new UserAuthenticationProvider(userAuthenticationManager));

        final AuthHandler clientAuthHandler = ChainAuthHandler.create()
                .append(ClientCredentialsAuthHandler.create(clientAuthProvider.getDelegate()))
                .append(ClientBasicAuthHandler.create(clientAuthProvider.getDelegate()));

        final AuthHandler userAuthHandler = RedirectAuthHandler.create(
                userAuthProvider.getDelegate(), '/' + domain.getPath() + "/login");

        // create other handlers
        final AuthorizationRequestParseHandler authorizationRequestParseHandler = AuthorizationRequestParseHandler.create();

        // Bind OAuth2 endpoints
        Handler<RoutingContext> authorizeEndpoint = new AuthorizeEndpointHandler(clientService, approvalService, authorizationCodeService, tokenGranter);
        Handler<RoutingContext> tokenEndpoint = new TokenEndpointHandler(tokenGranter);
        Handler<RoutingContext> userApprovalEndpoint = new UserApprovalEndpointHandler();

        // Check_token is provided only for backward compatibility and must be remove in the future
        Handler<RoutingContext> checkTokenEndpoint = new CheckTokenEndpointHandler();
        ((CheckTokenEndpointHandler) checkTokenEndpoint).setTokenService(tokenService);

        Handler<RoutingContext> introspectionEndpoint = new IntrospectionEndpointHandler();
        ((IntrospectionEndpointHandler) introspectionEndpoint).setIntrospectionService(introspectionService);

        router
                .route("/oauth/authorize")
                .handler(CookieHandler.create())
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(UserSessionHandler.create(userAuthProvider));

        router.route(HttpMethod.POST, "/oauth/authorize")
                .handler(authorizationRequestParseHandler)
                .handler(userAuthHandler)
                .handler(authorizeEndpoint);
        router.route(HttpMethod.GET,"/oauth/authorize")
                .handler(authorizationRequestParseHandler)
                .handler(userAuthHandler)
                .handler(authorizeEndpoint);
        router.route(HttpMethod.POST, "/oauth/token")
                .handler(clientAuthHandler)
                .handler(tokenEndpoint);
        router.route(HttpMethod.POST, "/oauth/check_token")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(clientAuthHandler)
                .handler(checkTokenEndpoint);
        router.route(HttpMethod.POST, "/oauth/introspect")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(clientAuthHandler)
                .handler(introspectionEndpoint);
        router.route(HttpMethod.GET, "/oauth/confirm_access")
                .handler(userApprovalEndpoint);
    }
}
