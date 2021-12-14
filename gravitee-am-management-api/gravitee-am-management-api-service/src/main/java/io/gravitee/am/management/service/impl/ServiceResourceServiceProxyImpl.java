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

package io.gravitee.am.management.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.AbstractSensitiveProxy;
import io.gravitee.am.management.service.ResourcePluginService;
import io.gravitee.am.management.service.ServiceResourceServiceProxy;
import io.gravitee.am.management.service.exception.ResourcePluginNotFoundException;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.service.ServiceResourceService;
import io.gravitee.am.service.exception.ServiceResourceNotFoundException;
import io.gravitee.am.service.model.NewServiceResource;
import io.gravitee.am.service.model.UpdateServiceResource;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ServiceResourceServiceProxyImpl extends AbstractSensitiveProxy implements ServiceResourceServiceProxy {

    @Autowired
    private ResourcePluginService resourcePluginService;

    @Autowired
    private ServiceResourceService serviceResourceService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Maybe<ServiceResource> findById(String id) {
        return serviceResourceService.findById(id).flatMap(reporter -> filterSensitiveData(reporter).toMaybe());
    }

    @Override
    public Flowable<ServiceResource> findByDomain(String domain) {
        return serviceResourceService.findByDomain(domain).flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Single<ServiceResource> create(String domain, NewServiceResource res, User principal) {
        return serviceResourceService.create(domain, res, principal).flatMap(this::filterSensitiveData);
    }

    @Override
    public Single<ServiceResource> update(String domain, String id, UpdateServiceResource updateServiceResource, User principal) {
        return serviceResourceService.findById(id)
                .switchIfEmpty(Single.error(new ServiceResourceNotFoundException(id)))
                .flatMap(oldResource -> updateSensitiveData(updateServiceResource, oldResource))
                .flatMap(resourceToUpdate -> serviceResourceService.update(domain, id, resourceToUpdate, principal))
                .flatMap(this::filterSensitiveData);
    }

    @Override
    public Completable delete(String domain, String resId, User principal) {
        return serviceResourceService.delete(domain, resId, principal);
    }

    private Single<ServiceResource> filterSensitiveData(ServiceResource serviceResource) {
        return resourcePluginService.getSchema(serviceResource.getType())
                .switchIfEmpty(Single.error(new ResourcePluginNotFoundException(serviceResource.getType())))
                .map(schema -> {
                    var schemaNode = objectMapper.readTree(schema);
                    var configurationNode = objectMapper.readTree(serviceResource.getConfiguration());
                    super.filterSensitiveData(schemaNode, configurationNode, serviceResource::setConfiguration);
                    return serviceResource;
                });
    }

    private Single<UpdateServiceResource> updateSensitiveData(UpdateServiceResource updateServiceResource, ServiceResource oldServiceResource) {
        return resourcePluginService.getSchema(oldServiceResource.getType())
                .switchIfEmpty(Single.error(new ResourcePluginNotFoundException(oldServiceResource.getType())))
                .map(schema -> {
                    var updateConfig = objectMapper.readTree(updateServiceResource.getConfiguration());
                    var oldConfig = objectMapper.readTree(oldServiceResource.getConfiguration());
                    var schemaConfig = objectMapper.readTree(schema);
                    super.updateSensitiveData(updateConfig, oldConfig, schemaConfig, updateServiceResource::setConfiguration);
                    return updateServiceResource;
                });
    }
}
