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
package io.gravitee.am.service.upgrader;

import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.node.api.upgrader.UpgradeRecord;
import io.gravitee.node.api.upgrader.Upgrader;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class AmUpgraderService extends AbstractService<AmUpgraderService> implements LifecycleComponent<AmUpgraderService> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AmUpgraderService.class);
    private final boolean upgraderModeEnabled;
    private final UpgraderRepository upgraderRepository;
    private final Class<? extends Annotation> qualifier;

    public AmUpgraderService(boolean upgraderModeEnabled,
                             UpgraderRepository upgraderRepository,
                             Class<? extends Annotation> qualifier) {
        this.upgraderModeEnabled = upgraderModeEnabled;
        this.upgraderRepository = upgraderRepository;
        this.qualifier = qualifier;
    }

    protected String name() {
        return "Upgrader service";
    }

    protected void doStart() throws Exception {
        super.doStart();
        AtomicBoolean stopUpgrade = new AtomicBoolean(false);
        this.applicationContext.getBeansWithAnnotation(qualifier)
                .values()
                .stream()
                .filter(bean -> bean instanceof Upgrader)
                .map(Upgrader.class::cast)
                .sorted(Comparator.comparing(Upgrader::getOrder))
                .takeWhile((upgrader) -> !stopUpgrade.get())
                .forEach((upgrader) -> {
                    if(!doUpgrade(upgrader)){
                        stopUpgrade.set(true);
                    }
                });
        if (upgraderModeEnabled || stopUpgrade.get()) {
            shutdown(stopUpgrade.get());
        }
    }

    private boolean doUpgrade(Upgrader upgrader) {
        String name = upgrader.getClass().getSimpleName();
        try {
            UpgradeRecord upgradeRecord = this.upgraderRepository.findById(upgrader.identifier()).blockingGet();
            if (upgradeRecord != null) {
                LOGGER.info("{} is already applied. it will be ignored.", name);
            } else {
                LOGGER.info("Apply {} ...", name);
                if (upgrader.upgrade()) {
                    this.upgraderRepository.create(new UpgradeRecord(upgrader.identifier(), new Date())).blockingGet();
                } else {
                    return false;
                }
            }
        } catch (Exception var5) {
            LOGGER.error("Unable to apply {}. Error: ", name, var5);
        }
        return true;
    }

    private void shutdown(boolean error) throws Exception {
        Node node = this.applicationContext.getBean(Node.class);
        node.preStop();
        node.stop();
        node.postStop();
        if (error) {
            LOGGER.error("Stopping because one of the upgrades could not be performed");
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

}
