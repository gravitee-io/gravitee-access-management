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
package io.gravitee.am.management.service;

import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.CertificateSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.repository.management.api.search.DomainCriteria;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.PatchDomain;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

import java.util.Collection;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DomainService extends DomainReadService {

    Flowable<Domain> findAllByEnvironment(String organizationId, String environment);

    Flowable<Domain> search(String organizationId, String environmentId, String query);

    Single<Domain> findByHrid(String environmentId, String hrid);

    /**
     * Use {@link #listAll()} instead
     */
    @Deprecated(forRemoval = true, since = "4.5")
    Single<List<Domain>> findAll();

    Flowable<Domain> findAllByCriteria(DomainCriteria criteria);

    Flowable<Domain> findByIdIn(Collection<String> ids);

    Single<Domain> create(String organizationId, String environmentId, NewDomain domain, User principal);

    Single<Domain> update(String domainId, Domain domain);

    Single<Domain> patch(GraviteeContext graviteeContext, String domainId, PatchDomain domain, User principal);

    Single<Domain> updateCertificateSettings(GraviteeContext graviteeContext, String domainId, CertificateSettings certificateSettings, User principal);

    Completable delete(GraviteeContext graviteeContext, String domain, User principal);

    default Single<Domain> create(String organizationId, String environmentId, NewDomain domain) {
        return create(organizationId, environmentId, domain, null);
    }

    /**
     * Filter a list of entrypoints depending on domain tags.
     * Given a domain with tags [ A, B ], then entrypoint must has either A or B tag defined.
     * If no entrypoint has been retained, the default entrypoint is returned, in that case
     * the entrypoint url may be overridden using the gateway.url linked to the Domain DataPlane
     *
     * @param domain the domain.
     * @param organizationId the internal id of the organizatio owning the entrypoints
     * @return a filtered list of entrypoints.
     */
    Single<List<Entrypoint>> listEntryPoint(Domain domain, String organizationId);
}
