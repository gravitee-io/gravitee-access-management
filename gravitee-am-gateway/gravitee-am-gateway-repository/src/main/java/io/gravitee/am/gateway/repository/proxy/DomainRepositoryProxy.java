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
package io.gravitee.am.gateway.repository.proxy;

import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.DomainRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DomainRepositoryProxy extends AbstractProxy<DomainRepository> implements DomainRepository {

    @Override
    public Set<Domain> findAll() throws TechnicalException {
        return target.findAll();
    }

    @Override
    public Set<Domain> findByIdIn(Collection<String> ids) throws TechnicalException {
        return target.findByIdIn(ids);
    }

    @Override
    public Optional<Domain> findById(String id) throws TechnicalException {
        return target.findById(id);
    }

    @Override
    public Domain create(Domain domain) throws TechnicalException {
        return this.target.create(domain);
    }

    @Override
    public Domain update(Domain domain) throws TechnicalException {
        return target.update(domain);
    }

    @Override
    public void delete(String id) throws TechnicalException {
        target.delete(id);
    }
}
