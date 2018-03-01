package io.gravitee.am.gateway.handler.idp.impl;

import io.gravitee.am.gateway.handler.idp.IdentityProviderManager;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.model.IdentityProvider;
import io.reactivex.Maybe;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderManagerImpl implements IdentityProviderManager {

    @Override
    public Maybe<AuthenticationProvider> get(String id) {
        //TODO: To implement
        return Maybe.empty();
    }

    @Override
    public Maybe<IdentityProvider> getIdentityProvider(String id) {
        //TODO: To implement
        return Maybe.empty();
    }
}
