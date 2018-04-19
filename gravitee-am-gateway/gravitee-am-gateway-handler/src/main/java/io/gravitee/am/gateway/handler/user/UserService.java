package io.gravitee.am.gateway.handler.user;

import io.gravitee.am.model.User;
import io.reactivex.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserService {

    /**
     * Used after a successful authentication.
     * Perhaps not the best place to put this method.
     *
     * @param user
     * @return
     */
    Single<User> findOrCreate(io.gravitee.am.identityprovider.api.User user);
}
