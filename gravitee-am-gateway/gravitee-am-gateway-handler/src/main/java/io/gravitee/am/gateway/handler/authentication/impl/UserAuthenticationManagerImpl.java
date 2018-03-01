package io.gravitee.am.gateway.handler.authentication.impl;

import io.gravitee.am.gateway.handler.authentication.UserAuthenticationManager;
import io.reactivex.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuthenticationManagerImpl implements UserAuthenticationManager {

    @Override
    public Single<Object> authenticate(String username, String password) {

    }
}
