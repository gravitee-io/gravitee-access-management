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
package io.gravitee.am.repository.mongodb.common;

import io.gravitee.node.api.upgrader.Upgrader;
import lombok.CustomLog;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
@CustomLog
public class MongoUpgraderConfiguration {
    public MongoUpgraderConfiguration(Optional<io.gravitee.node.api.configuration.Configuration> config,
                                      ApplicationContext pluginApplicationContext) {
        if (config.isEmpty()) {
            // test mode
            return;
        }
        //expose Upgrader beans from the plugin to the parent context, so they're visible to the UpgraderService
        Optional.ofNullable(pluginApplicationContext.getParent())
                .map(ConfigurableApplicationContext.class::cast)
                .map(ConfigurableApplicationContext::getBeanFactory)
                .ifPresent(parentBeanFactory -> registerUpgraders(pluginApplicationContext, parentBeanFactory));
    }

    private void registerUpgraders(ApplicationContext pluginContext, ConfigurableListableBeanFactory parentBeanFactory) {
        pluginContext.getBeansOfType(Upgrader.class).forEach((name, upgrader) -> {
            if (upgrader instanceof AbstractMongoIndexUpgrader indexUpgrader && !indexUpgrader.isEnabled()) {
                log.info("Mongo index management disabled for {} (ensureIndexOnStart=false)", indexUpgrader.getClass().getSimpleName());
                return;
            }
            parentBeanFactory.registerSingleton(name, upgrader);
        });
    }

}
