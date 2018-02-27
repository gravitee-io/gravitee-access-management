package io.gravitee.am.gateway.handler.oauth2.client.impl;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.model.Client;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientServiceImpl implements ClientService {

    @Override
    public Maybe<Client> findByClientId(String clientId) {
        return Maybe.empty();
    }
}
