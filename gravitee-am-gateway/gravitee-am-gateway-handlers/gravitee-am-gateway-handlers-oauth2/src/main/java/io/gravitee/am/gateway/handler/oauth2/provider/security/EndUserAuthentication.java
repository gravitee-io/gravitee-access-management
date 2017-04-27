package io.gravitee.am.gateway.handler.oauth2.provider.security;

import io.gravitee.am.identityprovider.api.Authentication;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
class EndUserAuthentication implements Authentication {

    private final Object principal;
    private final Object credentials;

    public EndUserAuthentication(Object principal, Object credentials) {
        this.principal = principal;
        this.credentials = credentials;
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public String toString() {
        return principal.toString();
    }
}
