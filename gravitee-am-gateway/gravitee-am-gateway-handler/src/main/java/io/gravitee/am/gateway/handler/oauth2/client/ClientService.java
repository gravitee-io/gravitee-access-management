package io.gravitee.am.gateway.handler.oauth2.client;

import io.gravitee.am.model.Client;
import io.reactivex.Maybe;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ClientService {

    Maybe<Client> findByClientId(String clientId);
}
