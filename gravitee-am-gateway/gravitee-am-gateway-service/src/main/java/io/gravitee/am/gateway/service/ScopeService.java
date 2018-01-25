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

import io.gravitee.am.gateway.service.model.NewScope;
import io.gravitee.am.gateway.service.model.UpdateScope;
import io.gravitee.am.model.oauth2.Scope;

import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ScopeService {

    Scope findById(String id);

    Scope create(String domain, NewScope scope);

    Set<Scope> findByDomain(String domain);

    Scope update(String domain, String id, UpdateScope updateScope);

    void delete(String scopeId);
}
