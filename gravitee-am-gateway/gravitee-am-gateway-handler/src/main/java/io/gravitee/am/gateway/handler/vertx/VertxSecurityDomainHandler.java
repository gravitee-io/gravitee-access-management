package io.gravitee.am.gateway.handler.vertx;

import io.gravitee.am.gateway.handler.authentication.InMemoryAuthenticationProvider;
import io.gravitee.am.gateway.handler.oauth2.auth.ClientAuthHandler;
import io.gravitee.am.gateway.handler.vertx.endpoint.TokenEndpointHandler;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.Router;
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

    public Router router() {
        Router router = Router.router(vertx);

        // Create the handlers
        final AuthProvider authProvider = createAuthProvider();
        final AuthHandler clientAuthProvider = createClientAuthHandler();

        setupCoreWebHandlers(router, authProvider);

//        final AuthHandler authHandler = RedirectAuthHandler.create(authProvider, loginURL);

        // auth protected paths
//        router.route("/oauth/authorize").handler(authHandler);

        // bind api
//        router.route("/oauth/authorize").handler(authorizer::authorize);

        router.route(HttpMethod.POST, "/oauth/token").handler(clientAuthProvider).handler(new TokenEndpointHandler());

        router.exceptionHandler(new Handler<Throwable>() {
            @Override
            public void handle(Throwable throwable) {
                System.out.println(throwable);
            }
        });

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

    private AuthHandler createClientAuthHandler() {
        return ClientAuthHandler.create(null);
    }

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }
}
