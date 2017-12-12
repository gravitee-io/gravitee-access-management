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
package io.gravitee.am.gateway.handler.oauth2.service;

import io.gravitee.am.gateway.service.ScopeService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DomainScopeService {

    private final Logger logger = LoggerFactory.getLogger(DomainScopeService.class);

    @Autowired
    private Domain domain;

    @Autowired
    private ScopeService scopeService;

    public Set<Scope> getAll() {
        logger.debug("Loading scopes for domain id[{}] name[{}]", domain.getId(), domain.getName());

        return scopeService.findByDomain(domain.getId());
    }
}
