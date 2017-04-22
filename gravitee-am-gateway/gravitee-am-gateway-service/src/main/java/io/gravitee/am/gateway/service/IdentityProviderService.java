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
package io.gravitee.am.gateway.service;

import io.gravitee.am.gateway.service.model.NewIdentityProvider;
import io.gravitee.am.gateway.service.model.UpdateIdentityProvider;
import io.gravitee.am.model.IdentityProvider;

import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface IdentityProviderService {

    IdentityProvider findById(String id);

    List<IdentityProvider> findByClient(String id);

    List<IdentityProvider> findByDomain(String domain);

    IdentityProvider create(String domain, NewIdentityProvider identityProvider);

    IdentityProvider update(String domain, String id, UpdateIdentityProvider updateIdentityProvider);
}
