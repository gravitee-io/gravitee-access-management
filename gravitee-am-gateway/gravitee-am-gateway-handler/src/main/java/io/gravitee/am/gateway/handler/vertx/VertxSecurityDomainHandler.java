package io.gravitee.am.gateway.handler.vertx;

import io.gravitee.am.gateway.handler.vertx.auth.handler.ClientCredentialsAuthHandler;
import io.gravitee.am.gateway.handler.vertx.auth.provider.ClientAuthenticationProvider;
import io.gravitee.am.gateway.handler.authentication.InMemoryAuthenticationProvider;
import io.gravitee.am.gateway.handler.vertx.auth.handler.ClientBasicAuthHandler;
import io.gravitee.am.gateway.handler.vertx.endpoint.AuthorizeEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.endpoint.TokenEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VertxSecurityDomainHandler {

    @Autowired
    private Vertx vertx;

    public Router oauth2(Router router) {
        // Create the handlers
        final AuthProvider authProvider = createAuthProvider();

        final AuthProvider clientAuthProvider = new ClientAuthenticationProvider();
        final AuthHandler clientAuthHandler = ChainAuthHandler.create()
                .append(ClientCredentialsAuthHandler.create(clientAuthProvider))
                .append(ClientBasicAuthHandler.create(clientAuthProvider));

        setupCoreWebHandlers(router, authProvider);

//        final AuthHandler authHandler = RedirectAuthHandler.create(authProvider, loginURL);

        // auth protected paths
//        router.route("/oauth/authorize").handler(authHandler);

        Handler<RoutingContext> authorizeEndpoint = new AuthorizeEndpointHandler();
        Handler<RoutingContext> tokenEndpoint = new TokenEndpointHandler();

        /*
        router.exceptionHandler(new Handler<Throwable>() {
            @Override
            public void handle(Throwable throwable) {
                System.out.println(throwable);
            }
        });
        */

        router.route().failureHandler(new ExceptionHandler());

        // Bind OAuth2 endpoints
        router.route(HttpMethod.POST, "/oauth/authorize").handler(authorizeEndpoint);
        router.route(HttpMethod.GET,"/oauth/authorize").handler(authorizeEndpoint);

        router.route(HttpMethod.POST, "/oauth/token").handler(clientAuthHandler).handler(tokenEndpoint);

//        router.route("/oauth/tokeninfo").handler(authorizer::tokenInfo);

        return router;
    }

    private void setupCoreWebHandlers(Router router, AuthProvider authProvider) {
        router.route().handler(CookieHandler.create());
        router.route().handler(BodyHandler.create());
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().handler(UserSessionHandler.create(authProvider));
    }

    private AuthProvider createAuthProvider() {
        return InMemoryAuthenticationProvider.create();
    }

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }
}
