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

package io.gravitee.am.plugins.dataplan.core;

import io.gravitee.am.dataplan.api.DataPlanDescription;
import io.gravitee.am.dataplan.api.provider.DataPlanProvider;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.configuration.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.util.StringUtils.hasText;

@Component
public class DataPlanLoader extends AbstractService<DataPlanLoader> {

    @Autowired
    private Configuration configuration;

    @Autowired
    private DataPlanPluginManager dataPlanPluginManager;

    // keep track of the DP plugin based on the DPID
    private Map<String, DataPlanProvider> dataPlanProviders = new HashMap<>();
    // useful to display DP in the UI
    private Map<String, DataPlanDescription> dataPlanDescriptions = new HashMap<>();

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // GW deployment
        if (configuration.containsProperty("dataPlan")) {
            final var dataPlanId = configuration.getProperty("dataPlan", String.class);
            // create from GW settings
            final var description = new DataPlanDescription(dataPlanId, dataPlanId, configuration.getProperty("repositories.gateway.type", String.class, "mongodb"), "repositories.gateway");
            dataPlanPluginManager.create(description)
                    .ifPresent(provider -> this.dataPlanProviders.put(description.id(), provider));
        } else {
            // mAPI deployment
            var index = 0;
            while (configuration.containsProperty(getPropertyBase(index)+".id")) {
                final var description = buildDescription(index);
                this.dataPlanDescriptions.put(description.id(), description);

                dataPlanPluginManager.create(description)
                        .ifPresent(provider -> this.dataPlanProviders.put(description.id(), provider));

                ++index;
            }
        }

        if (this.dataPlanProviders.isEmpty()) {
            throw new IllegalStateException("No DataPlan provider found");
        }
    }

    protected DataPlanDescription buildDescription(int index) {
        final var dataPlanId = configuration.getProperty(getPropertyBase(index)+".id", String.class);
        if (!hasText(dataPlanId)) {
            throw new IllegalStateException("Invalid data plan definition, id is required");
        }
        if (this.dataPlanProviders.containsKey(dataPlanId)) {
            throw new IllegalStateException("Invalid data plan definition, id must be unique");
        }
        final var dataPlanType = configuration.getProperty(getPropertyBase(index)+".type", String.class);
        if (!hasText(dataPlanType)) {
            throw new IllegalStateException("Invalid data plan definition, type is required");
        }

        final var dataPlanName = configuration.getProperty(getPropertyBase(index)+".name", String.class, dataPlanId);
        final var settingsPrefix = getPropertyBase(index);

        return new DataPlanDescription(dataPlanId, dataPlanName, dataPlanType, settingsPrefix);
    }

    protected String getPropertyBase(int index) {
        return "dataPlans[" + index + "]";
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        for(DataPlanProvider provider : dataPlanProviders.values()) {
            provider.stop();
        }
    }

    public Optional<DataPlanProvider> getDataPlanProvider(String id) {
        return Optional.ofNullable(dataPlanProviders.get(id));
    }

    public List<DataPlanDescription> listDataPlans() {
        return dataPlanDescriptions.values().stream().toList();
    }
}
