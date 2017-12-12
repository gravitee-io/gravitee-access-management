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

import io.gravitee.am.model.Client;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.exceptions.TechnicalException;

import java.util.Optional;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ClientRepository extends CrudRepository<Client, String> {

    /**
     * The full list of {@link Client} for a domain.
     *
     * @return All {@link Client}
     * @throws TechnicalException
     */
    Set<Client> findByDomain(String domain) throws TechnicalException;

    Page<Client> findByDomain(String domain, int page, int size) throws TechnicalException;

    Optional<Client> findByClientIdAndDomain(String clientId, String domain) throws TechnicalException;

    Set<Client> findByIdentityProvider(String identityProvider) throws TechnicalException;

    Set<Client> findByCertificate(String certificate) throws TechnicalException;

    Set<Client> findByExtensionGrant(String tokenGranter) throws TechnicalException;

    Set<Client> findAll() throws TechnicalException;

    Page<Client> findAll(int page, int size) throws TechnicalException;

    long countByDomain(String domain) throws TechnicalException;

    long count() throws TechnicalException;
}
