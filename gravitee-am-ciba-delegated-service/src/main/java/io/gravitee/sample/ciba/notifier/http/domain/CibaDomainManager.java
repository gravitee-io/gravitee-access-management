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
package io.gravitee.sample.ciba.notifier.http.domain;

import io.gravitee.sample.ciba.notifier.http.model.DomainReference;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CibaDomainManager {

    private Map<String, DomainReference> domains = new HashMap<>();

    public Optional<DomainReference> getDomainRef(String domain) {
        return Optional.ofNullable(this.domains.get(domain));
    }

    public void registerDomain(DomainReference ref) {
        this.domains.put(ref.getDomainId(), ref);
    }

    public void removeDomain(String domain) {
        this.domains.remove(domain);
    }

}
