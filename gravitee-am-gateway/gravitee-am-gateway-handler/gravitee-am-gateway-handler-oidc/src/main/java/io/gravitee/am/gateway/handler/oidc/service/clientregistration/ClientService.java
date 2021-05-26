/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.handler.oidc.service.clientregistration;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.ApplicationService;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * NOTE : this service must only be used in an OpenID Connect context
 * Use the {@link ApplicationService} for management purpose
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public interface ClientService {

    Maybe<Client> findById(String id);

    Single<Client> create(Client client);

    Single<Client> renewClientSecret(String domain, String id, User principal);

    Completable delete(String clientId, User principal);

    Single<Client> update(Client client);

    default Single<Client> renewClientSecret(String domain, String id) {
        return renewClientSecret(domain, id, null);
    }

    default Completable delete(String clientId) {
        return delete(clientId, null);
    }

}
