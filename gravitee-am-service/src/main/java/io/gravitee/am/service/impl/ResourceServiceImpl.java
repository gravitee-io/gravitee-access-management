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

import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.repository.management.api.ResourceRepository;
import io.gravitee.am.service.ResourceService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewResource;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Component
public class ResourceServiceImpl implements ResourceService {

    private final Logger LOGGER = LoggerFactory.getLogger(ResourceServiceImpl.class);

    @Lazy
    @Autowired
    ResourceRepository repository;

    @Autowired
    private ScopeService scopeService;

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
    public Single<List<Resource>> findByResources(List<String> resourceIds) {
        LOGGER.debug("Listing resource for resources {}", resourceIds);
        return repository.findByResources(resourceIds);
    }

    @Override
    public Single<List<Resource>> listByDomainAndClientAndUser(String domain, String client, String userId) {
        LOGGER.debug("Listing resource for resource owner {} and client {}", userId, client);
        return repository.findByDomainAndClientAndUser(domain, client, userId);
    }

    @Override
    public Single<List<Resource>> findByDomainAndClientAndUserAndResources(String domain, String client, String userId, List<String> resourceIds) {
        LOGGER.debug("Getting resource {} for resource owner {} and client {} and resources {}", resourceIds, userId, client, resourceIds);
        return repository.findByDomainAndClientAndUserAndResources(domain, client, userId, resourceIds);
    }

    @Override
    public Maybe<Resource> findByDomainAndClientAndUserAndResource(String domain, String client, String userId, String resourceId) {
        LOGGER.debug("Getting resource {} for resource owner {} and client {} and resource {}", resourceId, userId, client, resourceId);
        return repository.findByDomainAndClientAndUserAndResource(domain, client, userId, resourceId);
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
                .flatMap(repository::create);
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
        return repository.delete(resource.getId());
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
