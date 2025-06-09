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

package io.gravitee.am.gateway.handler.common.service.uma.impl;


import io.gravitee.am.dataplane.api.repository.AccessPolicyRepository;
import io.gravitee.am.dataplane.api.repository.ResourceRepository;
import io.gravitee.am.gateway.handler.common.service.uma.UMAResourceGatewayService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.gravitee.am.model.uma.policy.AccessPolicyType;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.AccessPolicyNotFoundException;
import io.gravitee.am.service.exception.MalformedIconUriException;
import io.gravitee.am.service.exception.MissingScopeException;
import io.gravitee.am.service.exception.ResourceNotFoundException;
import io.gravitee.am.service.exception.ScopeNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewResource;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class UMAResourceGatewayServiceImpl implements UMAResourceGatewayService, InitializingBean {

    private final Domain domain;
    private final DataPlaneRegistry dataPlaneRegistry;
    private final ScopeService scopeService;

    private ResourceRepository resourceRepository;
    private AccessPolicyRepository accessPolicyRepository;

    public UMAResourceGatewayServiceImpl(Domain domain, DataPlaneRegistry dataPlaneRegistry, ScopeService scopeService) {
        this.domain = domain;
        this.scopeService = scopeService;
        this.dataPlaneRegistry = dataPlaneRegistry;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.resourceRepository = this.dataPlaneRegistry.getResourceRepository(domain);
        this.accessPolicyRepository = this.dataPlaneRegistry.getAccessPolicyRepository(domain);
    }

    @Override
    public Single<Page<Resource>> findAll(int page, int size) {
        log.debug("Listing resource for domain {}", domain);
        return resourceRepository.findByDomain(domain.getId(), page, size)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find resources by domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find resources by domain %s", domain), ex));
                });
    }

    @Override
    public Flowable<Resource> findByResources(List<String> resourceIds) {
        log.debug("Listing resources by ids {}", resourceIds);
        return resourceRepository.findByResources(resourceIds);
    }

    @Override
    public Flowable<Resource> listByClientAndUser(String client, String userId) {
        log.debug("Listing resource for resource owner {} and client {}", userId, client);
        return resourceRepository.findByDomainAndClientAndUser(domain.getId(), client, userId);
    }

    @Override
    public Flowable<Resource> findByClientAndResources(String client, List<String> resourceIds) {
        log.debug("Getting resource {} for  client {} and resources {}", resourceIds, client, resourceIds);
        return resourceRepository.findByDomainAndClientAndResources(domain.getId(), client, resourceIds);
    }

    @Override
    public Maybe<Resource> findByClientAndUserAndResource(String client, String userId, String resourceId) {
        log.debug("Getting resource by resource owner {} and client {} and resource {}", userId, client, resourceId);
        return resourceRepository.findByDomainAndClientAndUserAndResource(domain.getId(), client, userId, resourceId);
    }

    @Override
    public Single<Resource> create(NewResource newResource, String client, String userId) {
        log.debug("Creating resource for resource owner {} and client {}", userId, client);
        Resource toCreate = new Resource();
        toCreate.setResourceScopes(newResource.getResourceScopes())
                .setDescription(newResource.getDescription())
                .setIconUri(newResource.getIconUri())
                .setName(newResource.getName())
                .setType(newResource.getType())
                .setDomain(domain.getId())
                .setClientId(client)
                .setUserId(userId)
                .setCreatedAt(new Date())
                .setUpdatedAt(toCreate.getCreatedAt());

        return this.validateScopes(toCreate)
                .flatMap(this::validateIconUri)
                .flatMap(resourceRepository::create)
                // create default policy
                .flatMap(r -> {
                    AccessPolicy accessPolicy = new AccessPolicy();
                    accessPolicy.setName("Deny all");
                    accessPolicy.setDescription("Default deny access policy. Created by Gravitee.io.");
                    accessPolicy.setType(AccessPolicyType.GROOVY);
                    accessPolicy.setCondition("{\"onRequestScript\":\"import io.gravitee.policy.groovy.PolicyResult.State\\nresult.state = State.FAILURE;\"}");
                    accessPolicy.setEnabled(true);
                    accessPolicy.setDomain(domain.getId());
                    accessPolicy.setResource(r.getId());
                    return accessPolicyRepository.create(accessPolicy).map(__ -> r);
                });
    }

    @Override
    public Single<Resource> update(NewResource newResource, String client, String userId, String resourceId) {
        log.debug("Updating resource id {} for resource owner {} and client {}", resourceId, userId, client);
        return findByClientAndUserAndResource(client, userId, resourceId)
                .switchIfEmpty(Single.error(new ResourceNotFoundException(resourceId)))
                .flatMap(Single::just)
                .map(newResource::update)
                .map(toUpdate -> toUpdate.setUpdatedAt(new Date()))
                .flatMap(this::validateScopes)
                .flatMap(this::validateIconUri)
                .flatMap(resourceRepository::update);
    }

    @Override
    public Completable delete(String client, String userId, String resourceId) {
        log.debug("Deleting resource id {} for resource owner {} and client {}", resourceId, userId, client);
        return findByClientAndUserAndResource(client, userId, resourceId)
                .switchIfEmpty(Maybe.error(new ResourceNotFoundException(resourceId)))
                .flatMapCompletable(found -> resourceRepository.delete(resourceId));    }

    @Override
    public Flowable<AccessPolicy> findAccessPolicies(String client, String user, String resource) {
        log.debug("Find access policies by domain {}, client {}, resource owner {} and resource id {}", domain.getId(), client, user, resource);
        return findByClientAndUserAndResource(client, user, resource)
                .switchIfEmpty(Single.error(new ResourceNotFoundException(resource)))
                .flatMapPublisher(r -> accessPolicyRepository.findByDomainAndResource(domain.getId(), r.getId()))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Flowable.error(ex);
                    }
                    log.error("An error has occurred while trying to find access policies by domain {}, client {}, resource owner {} and resource id {}", domain.getId(), client, user, resource, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error has occurred while trying to find access policies by domain %s, client %s, resource owner %s and resource id %s", domain, client, user, resource), ex));
                });
    }

    @Override
    public Flowable<AccessPolicy> findAccessPoliciesByResources(List<String> resourceIds) {
        log.debug("Find access policies by resources {}", resourceIds);
        return accessPolicyRepository.findByResources(resourceIds)
                .onErrorResumeNext(ex -> {
                    log.error("An error has occurred while trying to find access policies by resource ids {}", resourceIds, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error has occurred while trying to find access policies by resource ids %s", resourceIds), ex));
                });
    }

    @Override
    public Maybe<AccessPolicy> findAccessPolicy(String client, String user, String resource, String accessPolicy) {
        log.debug("Find access policy by domain {}, client {}, resource owner {}, resource id {} and policy id {}", domain.getId(), client, user, resource, accessPolicy);
        return findByClientAndUserAndResource(client, user, resource)
                .switchIfEmpty(Maybe.error(new ResourceNotFoundException(resource)))
                .flatMap(r -> accessPolicyRepository.findById(accessPolicy))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Maybe.error(ex);
                    }
                    log.error("An error has occurred while trying to find access policies by domain {}, client {}, resource owner {} and resource id {} and policy id {}", domain.getId(), client, user, resource, accessPolicy, ex);
                    return Maybe.error(new TechnicalManagementException(String.format("An error has occurred while trying to find access policies by domain %s, client %s, resource owner %s resource id %s and policy id %s", domain, client, user, resource, accessPolicy), ex));
                });    }

    @Override
    public Single<AccessPolicy> createAccessPolicy(AccessPolicy accessPolicy, String client, String user, String resource) {
        log.debug("Creating access policy for domain {}, client {}, resource owner {} and resource id {}", domain.getId(), client, user, resource);
        return findByClientAndUserAndResource(client, user, resource)
                .switchIfEmpty(Single.error(new ResourceNotFoundException(resource)))
                .flatMap(r -> {
                    accessPolicy.setDomain(domain.getId());
                    accessPolicy.setResource(r.getId());
                    accessPolicy.setCreatedAt(new Date());
                    accessPolicy.setUpdatedAt(accessPolicy.getCreatedAt());
                    return accessPolicyRepository.create(accessPolicy);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error has occurred while trying to create an access policy for domain {}, client {}, resource owner {} and resource id {}", domain.getId(), client, user, resource, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error has occurred while trying to create an access policy for domain %s, client %s, resource owner %s and resource id %s", domain.getId(), client, user, resource), ex));
                });
    }

    @Override
    public Single<AccessPolicy> updateAccessPolicy(AccessPolicy accessPolicy, String client, String user, String resource, String accessPolicyId) {
        log.debug("Updating access policy for domain {}, client {}, resource owner {}, resource id {} and policy id {}", domain.getId(), client, user, resource, accessPolicyId);
        return findByClientAndUserAndResource(client, user, resource)
                .switchIfEmpty(Maybe.error(new ResourceNotFoundException(resource)))
                .flatMap(r -> accessPolicyRepository.findById(accessPolicyId))
                .switchIfEmpty(Single.error(new AccessPolicyNotFoundException(resource)))
                .flatMap(oldPolicy -> {
                    AccessPolicy policyToUpdate = new AccessPolicy();
                    policyToUpdate.setId(oldPolicy.getId());
                    policyToUpdate.setEnabled(accessPolicy.isEnabled());
                    policyToUpdate.setName(accessPolicy.getName());
                    policyToUpdate.setDescription(accessPolicy.getDescription());
                    policyToUpdate.setType(accessPolicy.getType());
                    policyToUpdate.setOrder(accessPolicy.getOrder());
                    policyToUpdate.setCondition(accessPolicy.getCondition());
                    policyToUpdate.setDomain(oldPolicy.getDomain());
                    policyToUpdate.setResource(oldPolicy.getResource());
                    policyToUpdate.setCreatedAt(oldPolicy.getCreatedAt());
                    policyToUpdate.setUpdatedAt(new Date());
                    return accessPolicyRepository.update(policyToUpdate);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error has occurred while trying to update access policy for domain {}, client {}, resource owner {}, resource id {} and policy id {}", domain.getId(), client, user, resource, accessPolicyId, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error has occurred while trying to update access policy for domain %s, client %s, resource owner %s, resource id %s and policy id %s", domain.getId(), client, user, resource, accessPolicyId), ex));
                });
    }

    @Override
    public Completable deleteAccessPolicy(String client, String user, String resource, String accessPolicy) {
        log.debug("Deleting access policy for domain {}, client {}, user {}, resource owner {}, resource id and policy id {}", domain.getId(), client, user, resource, accessPolicy);
        return findByClientAndUserAndResource(client, user, resource)
                .switchIfEmpty(Maybe.error(new ResourceNotFoundException(resource)))
                .flatMapCompletable(__ -> accessPolicyRepository.delete(accessPolicy))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    log.error("An error has occurred while trying to delete access policy for domain {}, client {}, resource owner {}, resource id {} and policy id {}", domain.getId(), client, user, resource, accessPolicy, ex);
                    return Completable.error(new TechnicalManagementException(String.format("An error has occurred while trying to delete access policy for domain %s, client %s, resource owner %s, resource id %s and policy id %s", domain.getId(), client, user, resource, accessPolicy), ex));
                });
    }

    private Single<Resource> validateScopes(Resource toValidate) {
        if(toValidate.getResourceScopes()==null || toValidate.getResourceScopes().isEmpty()) {
            return Single.error(new MissingScopeException());
        }
        //Make sure they are distinct
        toValidate.setResourceScopes(toValidate.getResourceScopes().stream().distinct().collect(Collectors.toList()));

        return scopeService.findByDomainAndKeys(toValidate.getDomain(), toValidate.getResourceScopes())
                .flatMap(scopes -> {
                    if(toValidate.getResourceScopes().size() != scopes.size()) {
                        return Single.error(new ScopeNotFoundException(
                                toValidate.getResourceScopes().stream().filter(s -> !scopes.contains(s)).collect(Collectors.joining(","))
                        ));
                    }
                    return Single.just(toValidate);
                });
    }

    private Single<Resource> validateIconUri(Resource toValidate) {
        if(toValidate.getIconUri()!=null) {
            try {
                URI.create(toValidate.getIconUri()).toURL();
            } catch (MalformedURLException | IllegalArgumentException e) {
                return Single.error(new MalformedIconUriException(toValidate.getIconUri()));
            }
        }
        return Single.just(toValidate);
    }
}
