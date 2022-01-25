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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.gravitee.am.model.uma.policy.AccessPolicyType;
import io.gravitee.am.repository.management.api.AccessPolicyRepository;
import io.gravitee.am.repository.management.api.ResourceRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.ResourceService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewResource;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ResourceServiceImpl implements ResourceService {

    private final Logger LOGGER = LoggerFactory.getLogger(ResourceServiceImpl.class);

    @Lazy
    @Autowired
    private ResourceRepository repository;

    @Lazy
    @Autowired
    private AccessPolicyRepository accessPolicyRepository;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private UserService userService;

    @Autowired
    private ApplicationService applicationService;

    @Override
    public Single<Page<Resource>> findByDomain(String domain, int page, int size) {
        LOGGER.debug("Listing resource for domain {}", domain);
        return repository.findByDomain(domain, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find resources by domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find resources by domain %s", domain), ex));
                });
    }

    @Override
    public Single<Page<Resource>> findByDomainAndClient(String domain, String client, int page, int size) {
        LOGGER.debug("Listing resource set for domain {} and client {}", domain, client);
        return repository.findByDomainAndClient(domain, client, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find resources by domain {} and client {}", domain, client, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find resources by domain %s and client %s", domain, client), ex));
                });
    }

    @Override
    public Flowable<Resource> findByResources(List<String> resourceIds) {
        LOGGER.debug("Listing resources by ids {}", resourceIds);
        return repository.findByResources(resourceIds);
    }

    @Override
    public Flowable<Resource> listByDomainAndClientAndUser(String domain, String client, String userId) {
        LOGGER.debug("Listing resource for resource owner {} and client {}", userId, client);
        return repository.findByDomainAndClientAndUser(domain, client, userId);
    }

    @Override
    public Flowable<Resource> findByDomainAndClientAndResources(String domain, String client, List<String> resourceIds) {
        LOGGER.debug("Getting resource {} for  client {} and resources {}", resourceIds, client, resourceIds);
        return repository.findByDomainAndClientAndResources(domain, client, resourceIds);
    }

    @Override
    public Maybe<Resource> findByDomainAndClientAndUserAndResource(String domain, String client, String userId, String resourceId) {
        LOGGER.debug("Getting resource by resource owner {} and client {} and resource {}", userId, client, resourceId);
        return repository.findByDomainAndClientAndUserAndResource(domain, client, userId, resourceId);
    }

    @Override
    public Maybe<Resource> findByDomainAndClientResource(String domain, String client, String resourceId) {
        LOGGER.debug("Getting resource by domain {} client {} and resource {}", domain, client, resourceId);
        return this.findByDomainAndClientAndResources(domain, client, Arrays.asList(resourceId))
                .firstElement();
    }

    @Override
    public Single<Map<String, Map<String, Object>>> getMetadata(List<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            return Single.just(Collections.emptyMap());
        }

        List<String> userIds = resources.stream().filter(resource -> resource.getUserId() != null).map(Resource::getUserId).distinct().collect(Collectors.toList());
        List<String> appIds = resources.stream().filter(resource -> resource.getClientId() != null).map(Resource::getClientId).distinct().collect(Collectors.toList());
        return Single.zip(userService.findByIdIn(userIds).toMap(User::getId, this::filter),
                applicationService.findByIdIn(appIds).toMap(Application::getId, this::filter), (users, apps) -> {
            Map<String, Map<String, Object>> metadata = new HashMap<>();
            metadata.put("users", (Map) users);
            metadata.put("applications", (Map) apps);
            return metadata;
        });
    }

    @Override
    public Single<Resource> create(NewResource newResource, String domain, String client, String userId) {
        LOGGER.debug("Creating resource for resource owner {} and client {}", userId, client);
        Resource toCreate = new Resource();
        toCreate.setResourceScopes(newResource.getResourceScopes())
                .setDescription(newResource.getDescription())
                .setIconUri(newResource.getIconUri())
                .setName(newResource.getName())
                .setType(newResource.getType())
                .setDomain(domain)
                .setClientId(client)
                .setUserId(userId)
                .setCreatedAt(new Date())
                .setUpdatedAt(toCreate.getCreatedAt());

        return this.validateScopes(toCreate)
                .flatMap(this::validateIconUri)
                .flatMap(repository::create)
                // create default policy
                .flatMap(r -> {
                    AccessPolicy accessPolicy = new AccessPolicy();
                    accessPolicy.setName("Deny all");
                    accessPolicy.setDescription("Default deny access policy. Created by Gravitee.io.");
                    accessPolicy.setType(AccessPolicyType.GROOVY);
                    accessPolicy.setCondition("{\"onRequestScript\":\"import io.gravitee.policy.groovy.PolicyResult.State\\nresult.state = State.FAILURE;\"}");
                    accessPolicy.setEnabled(true);
                    accessPolicy.setDomain(domain);
                    accessPolicy.setResource(r.getId());
                    return accessPolicyRepository.create(accessPolicy).map(__ -> r);
                });
    }

    @Override
    public Single<Resource> update(NewResource newResource, String domain, String client, String userId, String resourceId) {
        LOGGER.debug("Updating resource id {} for resource owner {} and client {}", resourceId, userId, client);
        return findByDomainAndClientAndUserAndResource(domain, client, userId, resourceId)
                .switchIfEmpty(Maybe.error(new ResourceNotFoundException(resourceId)))
                .flatMapSingle(Single::just)
                .map(toUpdate -> newResource.update(toUpdate))
                .map(toUpdate -> toUpdate.setUpdatedAt(new Date()))
                .flatMap(this::validateScopes)
                .flatMap(this::validateIconUri)
                .flatMap(repository::update);
    }

    @Override
    public Single<Resource> update(Resource resource) {
        LOGGER.debug("Updating resource id {}", resource.getId());
        resource.setUpdatedAt(new Date());
        return repository.update(resource);
    }

    @Override
    public Completable delete(String domain, String client, String userId, String resourceId) {
        LOGGER.debug("Deleting resource id {} for resource owner {} and client {}", resourceId, userId, client);
        return findByDomainAndClientAndUserAndResource(domain, client, userId, resourceId)
                .switchIfEmpty(Maybe.error(new ResourceNotFoundException(resourceId)))
                .flatMapCompletable(found -> repository.delete(resourceId));
    }

    @Override
    public Completable delete(Resource resource) {
        LOGGER.debug("Deleting resource id {} on domain {}", resource.getId(), resource.getDomain());
        // delete policies and then the resource
        return accessPolicyRepository.findByDomainAndResource(resource.getDomain(), resource.getId())
                .flatMapCompletable(accessPolicy -> accessPolicyRepository.delete(accessPolicy.getId()))
                .andThen(repository.delete(resource.getId()));
    }

    @Override
    public Flowable<AccessPolicy> findAccessPolicies(String domain, String client, String user, String resource) {
        LOGGER.debug("Find access policies by domain {}, client {}, resource owner {} and resource id {}", domain, client, user, resource);
        return findByDomainAndClientAndUserAndResource(domain, client, user, resource)
                .switchIfEmpty(Single.error(new ResourceNotFoundException(resource)))
                .flatMapPublisher(r -> accessPolicyRepository.findByDomainAndResource(domain, r.getId()))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Flowable.error(ex);
                    }
                    LOGGER.error("An error has occurred while trying to find access policies by domain {}, client {}, resource owner {} and resource id {}", domain, client, user, resource, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error has occurred while trying to find access policies by domain %s, client %s, resource owner %s and resource id %s", domain, client, user, resource), ex));
                });
    }

    @Override
    public Flowable<AccessPolicy> findAccessPoliciesByResources(List<String> resourceIds) {
        LOGGER.debug("Find access policies by resources {}", resourceIds);
        return accessPolicyRepository.findByResources(resourceIds)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error has occurred while trying to find access policies by resource ids {}", resourceIds, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error has occurred while trying to find access policies by resource ids %s", resourceIds), ex));
                });
    }

    @Override
    public Single<Long> countAccessPolicyByResource(String resourceId) {
        LOGGER.debug("Count access policies by resource {}", resourceId);
        return accessPolicyRepository.countByResource(resourceId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error has occurred while trying to count access policies by resource id {}", resourceId, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error has occurred while trying to count access policies by resource id %s", resourceId), ex));
                });
    }

    @Override
    public Maybe<AccessPolicy> findAccessPolicy(String domain, String client, String user, String resource, String accessPolicy) {
        LOGGER.debug("Find access policy by domain {}, client {}, resource owner {}, resource id {} and policy id {}", domain, client, user, resource, accessPolicy);
        return findByDomainAndClientAndUserAndResource(domain, client, user, resource)
                .switchIfEmpty(Maybe.error(new ResourceNotFoundException(resource)))
                .flatMap(r -> accessPolicyRepository.findById(accessPolicy))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Maybe.error(ex);
                    }
                    LOGGER.error("An error has occurred while trying to find access policies by domain {}, client {}, resource owner {} and resource id {} and policy id {}", domain, client, user, resource, accessPolicy, ex);
                    return Maybe.error(new TechnicalManagementException(String.format("An error has occurred while trying to find access policies by domain %s, client %s, resource owner %s resource id %s and policy id %s", domain, client, user, resource, accessPolicy), ex));
                });
    }

    @Override
    public Maybe<AccessPolicy> findAccessPolicy(String accessPolicy) {
        LOGGER.debug("Find access policy by id {}", accessPolicy);
        return accessPolicyRepository.findById(accessPolicy)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error has occurred while trying to find access policy by id {}", accessPolicy, ex);
                    return Maybe.error(new TechnicalManagementException(String.format("An error has occurred while trying to find access policy by id %s", accessPolicy), ex));
                });
    }

    @Override
    public Single<AccessPolicy> createAccessPolicy(AccessPolicy accessPolicy, String domain, String client, String user, String resource) {
        LOGGER.debug("Creating access policy for domain {}, client {}, resource owner {} and resource id {}", domain, client, user, resource);
        return findByDomainAndClientAndUserAndResource(domain, client, user, resource)
                .switchIfEmpty(Single.error(new ResourceNotFoundException(resource)))
                .flatMap(r -> {
                    accessPolicy.setDomain(domain);
                    accessPolicy.setResource(r.getId());
                    accessPolicy.setCreatedAt(new Date());
                    accessPolicy.setUpdatedAt(accessPolicy.getCreatedAt());
                    return accessPolicyRepository.create(accessPolicy);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error has occurred while trying to create an access policy for domain {}, client {}, resource owner {} and resource id {}", domain, client, user, resource, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error has occurred while trying to create an access policy for domain %s, client %s, resource owner %s and resource id %s", domain, client, user, resource), ex));
                });
    }

    @Override
    public Single<AccessPolicy> updateAccessPolicy(AccessPolicy accessPolicy, String domain, String client, String user, String resource, String accessPolicyId) {
        LOGGER.debug("Updating access policy for domain {}, client {}, resource owner {}, resource id {} and policy id {}", domain, client, user, resource, accessPolicyId);
        return findByDomainAndClientAndUserAndResource(domain, client, user, resource)
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
                    LOGGER.error("An error has occurred while trying to update access policy for domain {}, client {}, resource owner {}, resource id {} and policy id {}", domain, client, user, resource, accessPolicyId, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error has occurred while trying to update access policy for domain %s, client %s, resource owner %s, resource id %s and policy id %s", domain, client, user, resource, accessPolicyId), ex));
                });
    }

    @Override
    public Completable deleteAccessPolicy(String domain, String client, String user, String resource, String accessPolicy) {
        LOGGER.debug("Deleting access policy for domain {}, client {}, resource owner {}, resource id and policy id {}", domain, client, user, resource, accessPolicy);
        return findByDomainAndClientAndUserAndResource(domain, client, user, resource)
                .switchIfEmpty(Maybe.error(new ResourceNotFoundException(resource)))
                .flatMapCompletable(__ -> accessPolicyRepository.delete(accessPolicy))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error has occurred while trying to delete access policy for domain {}, client {}, resource owner {}, resource id {} and policy id {}", domain, client, user, resource, accessPolicy, ex);
                    return Completable.error(new TechnicalManagementException(String.format("An error has occurred while trying to delete access policy for domain %s, client %s, resource owner %s, resource id %s and policy id %s", domain, client, user, resource, accessPolicy), ex));
                });
    }

    @Override
    public Completable deleteByDomain(String domain) {
        return accessPolicyRepository.deleteByDomain(domain);
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

    private User filter(User user) {
        User resourceOwner = new User();
        resourceOwner.setId(user.getId());
        resourceOwner.setDisplayName(user.getDisplayName());
        return resourceOwner;
    }

    private Application filter(Application application) {
        Application client = new Application();
        client.setId(application.getId());
        client.setName(application.getName());
        return client;
    }
}
