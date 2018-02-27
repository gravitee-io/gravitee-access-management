package io.gravitee.am.gateway.handler.oauth2.auth;

import io.gravitee.am.gateway.handler.oauth2.auth.impl.ClientAuthHandlerImpl;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.handler.AuthHandler;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ClientAuthHandler extends AuthHandler {

    /**
     * Create an oauth2 client auth handler
     *
     * @param authProvider  the auth provider to use
     * @return the auth handler
     */
    static AuthHandler create(AuthProvider authProvider) {
        return new ClientAuthHandlerImpl(authProvider);
    }
}
