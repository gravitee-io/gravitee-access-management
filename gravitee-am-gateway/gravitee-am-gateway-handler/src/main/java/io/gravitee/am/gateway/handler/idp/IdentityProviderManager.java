package io.gravitee.am.gateway.handler.idp;

import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.model.IdentityProvider;
import io.reactivex.Maybe;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface IdentityProviderManager {

    Maybe<AuthenticationProvider> get(String id);

    Maybe<IdentityProvider> getIdentityProvider(String id);
}
