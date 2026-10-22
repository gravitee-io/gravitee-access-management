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
import lombok.CustomLog;

import java.util.List;
import java.util.function.Supplier;

@CustomLog
public class DataPlaneUpgraderCheckService extends AbstractService<DataPlaneUpgraderCheckService> implements LifecycleComponent<DataPlaneUpgraderCheckService> {
    private final Supplier<List<DataPlaneProvider>> dataPlanesSupplier;
    private final DataPlaneUpgraderRegistry upgraderRegistry = new DataPlaneUpgraderRegistry();

    public DataPlaneUpgraderCheckService(Supplier<List<DataPlaneProvider>> dataPlanesSupplier) {
        this.dataPlanesSupplier = dataPlanesSupplier;
    }

    @Override
    protected String name() {
        return "DataPlane Upgrader check service";
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        dataPlanesSupplier.get().forEach(dataPlane -> upgraderRegistry.getDataPlaneUpgraders(dataPlane)
                .forEach(upgrader -> check(dataPlane, upgrader)));
    }

    private void check(DataPlaneProvider dataPlaneProvider, DataPlaneUpgrader upgrader) {
        final String name = upgrader.getClass().getSimpleName();
        final String identifier = upgrader.identifier(dataPlaneProvider.getDataPlaneDescription());
        try {
            if (dataPlaneProvider.getUpgraderRepository().findById(identifier).isEmpty().blockingGet()) {
                log.warn("{} has not been applied (id={}) and upgraders are disabled on this node (upgrade.mode=false). Run the upgrader with upgrade.mode=true, or start a node with upgrade.mode unset.",
                        name, identifier);
            }
        } catch (Exception e) {
            log.warn("Unable to check whether {} has been applied", name, e);
        }
    }
}
