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

package io.gravitee.am.management.service.dataplane.impl;


import io.gravitee.am.management.service.dataplane.UMAResourceManagementService;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class UMAResourceManagementServiceImpl implements UMAResourceManagementService {

    @Autowired
    private DataPlaneRegistry dataPlaneRegistry;

    @Autowired
    private ApplicationService applicationService;

    @Override
    public Flowable<AccessPolicy> findAccessPoliciesByResources(Domain domain, List<String> resourceIds) {
        log.debug("Find access policies on domain {} by resources {}", domain.getId(), resourceIds);
        return dataPlaneRegistry.getAccessPolicyRepository(domain).findByResources(resourceIds)
                .onErrorResumeNext(ex -> {
                    log.error("An error has occurred while trying to find access policies by resource ids {}", resourceIds, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error has occurred while trying to find access policies by resource ids %s", resourceIds), ex));
                });    }

    @Override
    public Maybe<AccessPolicy> findAccessPolicy(Domain domain, String accessPolicy) {
        log.debug("Find access policy on domain {} with id {}", domain.getId(), accessPolicy);
        return dataPlaneRegistry.getAccessPolicyRepository(domain).findById(accessPolicy)
                .onErrorResumeNext(ex -> {
                    log.error("An error has occurred while trying to find access policy by id {}", accessPolicy, ex);
                    return Maybe.error(new TechnicalManagementException(String.format("An error has occurred while trying to find access policy by id %s", accessPolicy), ex));
                });
    }

    @Override
    public Maybe<Resource> findByDomainAndClientResource(Domain domain, String client, String resourceId) {
        log.debug("Getting resource by domain {} client {} and resource {}", domain.getId(), client, resourceId);
        return dataPlaneRegistry.getResourceRepository(domain)
                .findByDomainAndClientAndResources(domain.getId(), client, List.of(resourceId))
                .firstElement();
    }

    @Override
    public Single<Page<Resource>> findByDomainAndClient(Domain domain, String client, int page, int size) {
        log.debug("Listing resource set for domain {} and client {}", domain.getId(), client);
        return dataPlaneRegistry.getResourceRepository(domain).findByDomainAndClient(domain.getId(), client, page, size)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find resources by domain {} and client {}", domain, client, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find resources by domain %s and client %s", domain, client), ex));
                });
    }

    @Override
    public Single<Long> countAccessPolicyByResource(Domain domain, String resourceId) {
        log.debug("Count access policies on domain {} by resource {}", domain.getId(), resourceId);
        return dataPlaneRegistry.getAccessPolicyRepository(domain).countByResource(resourceId)
                .onErrorResumeNext(ex -> {
                    log.error("An error has occurred while trying to count access policies by resource id {}", resourceId, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error has occurred while trying to count access policies by resource id %s", resourceId), ex));
                });
    }

    @Override
    public Flowable<Resource> findByDomain(Domain domain) {
        log.debug("Listing resource for domain {}", domain.getId());
        return dataPlaneRegistry.getResourceRepository(domain).findByDomain(domain.getId())
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find resources by domain {}", domain, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find resources by domain %s", domain), ex));
                });
    }

    @Override
    public Completable delete(Domain domain, Resource resource) {
        log.debug("Deleting resource id {} on domain {}", resource.getId(), resource.getDomain());
        // delete policies and then the resource
        final var accessPolicyRepository = dataPlaneRegistry.getAccessPolicyRepository(domain);
        final var resourceRepository = dataPlaneRegistry.getResourceRepository(domain);
        return accessPolicyRepository.findByDomainAndResource(resource.getDomain(), resource.getId())
                .flatMapCompletable(accessPolicy -> accessPolicyRepository.delete(accessPolicy.getId()))
                .andThen(resourceRepository.delete(resource.getId()));
    }

    @Override
    public Single<Map<String, Map<String, Object>>> getMetadata(Domain domain, List<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            return Single.just(Collections.emptyMap());
        }

        List<String> userIds = resources.stream().filter(resource -> resource.getUserId() != null).map(Resource::getUserId).distinct().collect(Collectors.toList());
        List<String> appIds = resources.stream().filter(resource -> resource.getClientId() != null).map(Resource::getClientId).distinct().collect(Collectors.toList());
        return Single.zip(dataPlaneRegistry.getUserRepository(domain).findByIdIn(Reference.domain(domain.getId()), userIds).toMap(User::getId, this::filter),
                applicationService.findByIdIn(appIds).toMap(Application::getId, this::filter), (users, apps) -> {
                    Map<String, Map<String, Object>> metadata = new HashMap<>();
                    metadata.put("users", (Map) users);
                    metadata.put("applications", (Map) apps);
                    return metadata;
                });
    }

    private Application filter(Application application) {
        Application client = new Application();
        client.setId(application.getId());
        client.setName(application.getName());
        return client;
    }

    private User filter(User user) {
        User resourceOwner = new User();
        resourceOwner.setId(user.getId());
        resourceOwner.setDisplayName(user.getDisplayName());
        return resourceOwner;
    }
}
