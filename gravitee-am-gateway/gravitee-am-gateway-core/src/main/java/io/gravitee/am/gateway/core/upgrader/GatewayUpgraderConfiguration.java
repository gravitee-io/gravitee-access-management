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
package io.gravitee.am.gateway.core.upgrader;

import io.gravitee.am.dataplane.api.upgrader.DataPlaneUpgraderService;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.upgrader.AmUpgraderService;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class GatewayUpgraderConfiguration {

    public static List<Class<? extends LifecycleComponent>> getComponents() {
        List<Class<? extends LifecycleComponent>> components = new ArrayList<>();

        String upgradeMode = System.getenv().getOrDefault("upgrade.mode", System.getProperty("upgrade.mode"));

        if (upgradeMode == null || "true".equalsIgnoreCase(upgradeMode)) {
            components.add(AmUpgraderService.class);
            components.add(DataPlaneUpgraderService.class);
        }

        if ("false".equalsIgnoreCase(upgradeMode)) {
            System.setProperty("liquibase.enabled", "false");
        }

        return components;
    }

    @Bean
    public AmUpgraderService upgraderService(@Lazy @Qualifier("gatewayUpgraderRepository") UpgraderRepository upgraderRepository){
        return new AmUpgraderService(false, upgraderRepository);
    }

    @Bean
    public DataPlaneUpgraderService dataPlaneUpgraderService(io.gravitee.node.api.configuration.Configuration configuration,
                                                             DataPlaneRegistry dataPlaneRegistry){
        boolean upgraderMode = configuration.getProperty("upgrade.mode", Boolean.class, false);
        return new DataPlaneUpgraderService(upgraderMode, dataPlaneRegistry::getAllProviders);
    }
}
