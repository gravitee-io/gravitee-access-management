package io.gravitee.am.gateway.handler.auth;

import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.User;
import io.reactivex.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserAuthenticationManager {

    Single<User> authenticate(String clientId, Authentication authentication);
}
