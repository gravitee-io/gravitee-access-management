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

package io.gravitee.am.service.validators.virtualhost;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.service.validators.Validator;
import io.reactivex.Completable;

import java.util.List;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface VirtualHostValidator extends Validator<VirtualHost, Completable> {

    default Completable validate(VirtualHost element) {
        return validate(element, List.of());
    }

    Completable validate(VirtualHost virtualHost, List<String> domainRestrictions);

    Completable validateDomainVhosts(Domain domain, List<Domain> domains);

    boolean isValidDomainOrSubDomain(String domain, List<String> domains);

}
