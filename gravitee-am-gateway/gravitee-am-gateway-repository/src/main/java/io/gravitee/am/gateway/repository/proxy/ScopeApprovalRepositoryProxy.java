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

import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ScopeApprovalRepositoryProxy extends AbstractProxy<ScopeApprovalRepository> implements ScopeApprovalRepository {

    public Optional<ScopeApproval> findById(String s) throws TechnicalException {
        return target.findById(s);
    }

    public ScopeApproval create(ScopeApproval item) throws TechnicalException {
        return target.create(item);
    }

    public ScopeApproval update(ScopeApproval item) throws TechnicalException {
        return target.update(item);
    }

    public void delete(String s) throws TechnicalException {
        target.delete(s);
    }

    public Set<ScopeApproval> findByDomainAndUserAndClient(String domain, String userId, String clientId) throws TechnicalException {
        return target.findByDomainAndUserAndClient(domain, userId, clientId);
    }

}
