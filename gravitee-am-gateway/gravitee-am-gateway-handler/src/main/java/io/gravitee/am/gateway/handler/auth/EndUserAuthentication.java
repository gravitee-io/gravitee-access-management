package io.gravitee.am.gateway.handler.auth;

import io.gravitee.am.identityprovider.api.Authentication;

import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class EndUserAuthentication implements Authentication {

    private final Object principal;
    private final Object credentials;
    private Map<String, Object> additionalInformation;

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
    public Map<String, Object> getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(Map<String, Object> additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    @Override
    public String toString() {
        return principal.toString();
    }
}
