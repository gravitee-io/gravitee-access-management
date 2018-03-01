package io.gravitee.am.gateway.handler.authentication;

import io.reactivex.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserAuthenticationManager {

    Single<Object> authenticate(String username, String password);
}
