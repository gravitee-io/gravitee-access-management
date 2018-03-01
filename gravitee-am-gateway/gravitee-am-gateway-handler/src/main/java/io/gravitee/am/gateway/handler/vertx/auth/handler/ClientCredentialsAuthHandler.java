package io.gravitee.am.gateway.handler.vertx.auth.handler;

import io.gravitee.am.gateway.handler.vertx.auth.handler.impl.ClientCredentialsAuthHandlerImpl;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.handler.AuthHandler;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ClientCredentialsAuthHandler extends AuthHandler {

    /**
     * Create an oauth2 client auth handler based on request parameters.
     *
     * @param authProvider  the auth provider to use
     * @return the auth handler
     */
    static AuthHandler create(AuthProvider authProvider) {
        return new ClientCredentialsAuthHandlerImpl(authProvider);
    }
}
