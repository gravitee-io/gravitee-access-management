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
package io.gravitee.am.plugins.dataplane.core;

import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.configuration.Configuration;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

abstract class DataPlaneLoader extends AbstractService<DataPlaneLoader> {

    @Autowired
    protected Configuration configuration;

    @Autowired
    private DataPlanePluginManager dataPlanePluginManager;

    private Map<String, DataPlaneProvider> dataPlanProviders = new ConcurrentHashMap<>();


    protected abstract void register();

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        register();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        for(DataPlaneProvider provider : dataPlanProviders.values()) {
            provider.stop();
        }
    }

    void create(DataPlaneDescription description){
        if (description.id() == null) {
            throw new IllegalStateException("Invalid data plan definition, id must be specified");
        }
        if (this.dataPlanProviders.containsKey(description.id())) {
            throw new IllegalStateException("Invalid data plan definition, id must be unique");
        }
        dataPlanePluginManager.create(description).ifPresent(provider -> this.dataPlanProviders.put(description.id(), provider));
    }


}
