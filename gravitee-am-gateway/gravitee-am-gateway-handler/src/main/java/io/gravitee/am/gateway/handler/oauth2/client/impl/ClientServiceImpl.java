package io.gravitee.am.gateway.handler.oauth2.client.impl;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.model.Client;
import io.reactivex.Maybe;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientServiceImpl implements ClientService {

    @Override
    public Maybe<Client> findByClientId(String clientId) {
        Client client = new Client();
        client.setClientId(clientId);
        return Maybe.just(client);
    }
}
