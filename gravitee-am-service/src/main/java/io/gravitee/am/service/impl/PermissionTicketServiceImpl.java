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

import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.model.uma.PermissionTicket;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.repository.management.api.PermissionTicketRepository;
import io.gravitee.am.service.PermissionTicketService;
import io.gravitee.am.service.ResourceService;
import io.gravitee.am.service.exception.InvalidPermissionRequestException;
import io.gravitee.am.service.exception.InvalidPermissionTicketException;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Component
public class PermissionTicketServiceImpl implements PermissionTicketService {

    @Value("${uma.permission.validity:60000}")
    private int umaPermissionValidity;

    @Lazy
    @Autowired
    private PermissionTicketRepository repository;

    @Autowired
    private ResourceService resourceService;

    @Override
    public Single<PermissionTicket> create(List<PermissionRequest> requestedPermission, String domain, String client, String userId) {
        //Get list of requested resources (same Id may appear twice with difference scopes)
        List<String> requestedResourcesIds = requestedPermission.stream().map(PermissionRequest::getResourceId).collect(Collectors.toList());
        //Compare with current registered resource set and return permission ticket if everything's correct.
        return resourceService.findByDomainAndClientAndUserAndResources(domain, client, userId, requestedResourcesIds)
                .flatMap(fetchedResourceSet -> this.validatePermissionRequest(requestedPermission, fetchedResourceSet, requestedResourcesIds))
                .map(permissionRequests -> {
                    PermissionTicket toCreate = new PermissionTicket();
                    return toCreate.setPermissionRequest(permissionRequests)
                            .setDomain(domain)
                            .setClientId(client)
                            .setUserId(userId)
                            .setCreatedAt(new Date())
                            .setExpireAt(new Date(System.currentTimeMillis()+umaPermissionValidity));
                    }
                ).flatMap(repository::create);
    }

    @Override
    public Maybe<PermissionTicket> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Single<PermissionTicket> remove(String id) {
        return repository.findById(id)
                .switchIfEmpty(Maybe.error(new InvalidPermissionTicketException()))
                .flatMapSingle(permissionTicket -> repository.delete(permissionTicket.getId()).andThen(Single.just(permissionTicket)));
    }

    /**
     * Validate if all requested resources are known and contains the requested scopes.
     * @param requestedPermissions Requested resources and associated scopes.
     * @param registeredResources Current registered resource sets.
     * @param requestedResourcesIds List of current requested resource set ids.
     * @return Permission requests input parameter if ok, else an error.
     */
    private Single<List<PermissionRequest>> validatePermissionRequest(List<PermissionRequest> requestedPermissions, List<Resource> registeredResources, List<String> requestedResourcesIds) {
        //Build map with resource ID as key.
        Map<String, Resource> resourceSetMap = registeredResources.stream().collect(Collectors.toMap(Resource::getId, resource -> resource));

        //If the fetched resources does not contains all the requested ids, then return an invalid resource id error.
        if(!resourceSetMap.keySet().containsAll(requestedResourcesIds)) {
            return Single.error(InvalidPermissionRequestException.INVALID_RESOURCE_ID);
        }

        //If current resource set does not contains all the requested scopes, then return an invalid scope error.
        for(PermissionRequest requestResourceScope:requestedPermissions) {
            Resource fetchedResource = resourceSetMap.get(requestResourceScope.getResourceId());
            if(!fetchedResource.getResourceScopes().containsAll(requestResourceScope.getResourceScopes())) {
                return Single.error(InvalidPermissionRequestException.INVALID_SCOPE_RESOURCE);
            }
        }

        //Everything is matching.
        return Single.just(requestedPermissions);
    }
}
