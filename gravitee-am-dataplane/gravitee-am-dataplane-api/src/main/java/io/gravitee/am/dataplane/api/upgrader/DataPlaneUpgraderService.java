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
package io.gravitee.am.dataplane.api.upgrader;

import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.upgrader.UpgradeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class DataPlaneUpgraderService extends AbstractService<DataPlaneUpgraderService> implements LifecycleComponent<DataPlaneUpgraderService> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataPlaneUpgraderService.class);
    private final boolean upgraderModeEnabled;
    private final Supplier<List<DataPlaneProvider>> dataPlanesSupplier;
    private final DataPlaneUpgraderRegistry upgraderRegistry = new DataPlaneUpgraderRegistry();

    public DataPlaneUpgraderService(boolean upgraderModeEnabled,
                                    Supplier<List<DataPlaneProvider>> dataPlanesSupplier) {
        this.upgraderModeEnabled = upgraderModeEnabled;
        this.dataPlanesSupplier = dataPlanesSupplier;
    }


    protected String name() {
        return "DataPlane Upgrader service";
    }

    protected void doStart() throws Exception {
        super.doStart();
        AtomicBoolean stopUpgrade = new AtomicBoolean(false);
        dataPlanesSupplier
                .get()
                .stream()
                .takeWhile((dp) -> !stopUpgrade.get())
                .forEach(dp -> upgrade(stopUpgrade, dp));
        if (upgraderModeEnabled || stopUpgrade.get()) {
            shutdown(stopUpgrade.get());
        }
    }

    private void upgrade(AtomicBoolean stopUpgrade, DataPlaneProvider dataPlaneProvider) {
         upgraderRegistry.getDataPlaneUpgraders()
                .stream()
                .takeWhile((upgrader) -> !stopUpgrade.get())
                .forEach((upgrader) -> {
                    if(!doUpgrade(dataPlaneProvider, upgrader)){
                        stopUpgrade.set(true);
                    }
                });
    }

    private boolean doUpgrade(DataPlaneProvider dataPlaneProvider, DataPlaneUpgrader upgrader){
        String name = upgrader.getClass().getSimpleName();

        try {
            String identifier = upgrader.identifier(dataPlaneProvider.getDataPlaneDescription());
            UpgradeRecord upgradeRecord = dataPlaneProvider.getUpgraderRepository().findById(identifier).blockingGet();
            if (upgradeRecord != null) {
                LOGGER.info("{} is already applied. it will be ignored.", name);
            } else {
                LOGGER.info("Apply {} ...", name);
                if (upgrader.upgrade(dataPlaneProvider)) {
                    dataPlaneProvider.getUpgraderRepository().create(new UpgradeRecord(identifier, new Date())).blockingGet();
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