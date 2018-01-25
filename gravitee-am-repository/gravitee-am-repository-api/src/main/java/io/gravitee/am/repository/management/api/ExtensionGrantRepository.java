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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.exceptions.TechnicalException;

import java.util.Optional;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ExtensionGrantRepository extends CrudRepository<ExtensionGrant, String> {

    /**
     * The full list of {@link ExtensionGrant} for a domain.
     *
     * @return All {@link ExtensionGrant}
     * @throws TechnicalException
     */
    Set<ExtensionGrant> findByDomain(String domain) throws TechnicalException;

    Optional<ExtensionGrant> findByDomainAndGrantType(String domain, String grantType) throws TechnicalException;
}
