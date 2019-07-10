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

import io.gravitee.am.model.Client;
import io.reactivex.Single;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public interface DynamicClientRegistrationService {

    Single<Client> create(DynamicClientRegistrationRequest request, String basePath);

    Single<Client> patch(Client toPatch, DynamicClientRegistrationRequest request, String basePath);

    Single<Client> update(Client toUpdate, DynamicClientRegistrationRequest request, String basePath);

    Single<Client> delete(Client toDelete);

    Single<Client> renewSecret(Client toRenew, String basePath);
}
