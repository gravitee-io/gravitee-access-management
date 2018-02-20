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
package io.gravitee.am.management.repository.proxy;

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class IdentityProviderRepositoryProxy extends AbstractProxy<IdentityProviderRepository> implements IdentityProviderRepository {

    @Override
    public Set<IdentityProvider> findByDomain(String domain) throws TechnicalException {
        return target.findByDomain(domain);
    }

    @Override
    public Optional<IdentityProvider> findById(String id) throws TechnicalException {
        return target.findById(id);
    }

    @Override
    public IdentityProvider create(IdentityProvider identityProvider) throws TechnicalException {
        return target.create(identityProvider);
    }

    @Override
    public IdentityProvider update(IdentityProvider identityProvider) throws TechnicalException {
        return target.update(identityProvider);
    }

    @Override
    public void delete(String id) throws TechnicalException {
        target.delete(id);
    }
}