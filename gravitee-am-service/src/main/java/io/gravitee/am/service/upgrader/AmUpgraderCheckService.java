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
import io.gravitee.node.api.upgrader.Upgrader;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import lombok.CustomLog;

import java.lang.annotation.Annotation;
import java.util.Comparator;

@CustomLog
public class AmUpgraderCheckService extends AbstractService<AmUpgraderCheckService> implements LifecycleComponent<AmUpgraderCheckService> {
    private final UpgraderRepository upgraderRepository;
    private final Class<? extends Annotation> qualifier;

    public AmUpgraderCheckService(UpgraderRepository upgraderRepository, Class<? extends Annotation> qualifier) {
        this.upgraderRepository = upgraderRepository;
        this.qualifier = qualifier;
    }

    @Override
    protected String name() {
        return "Upgrader check service";
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.applicationContext.getBeansWithAnnotation(qualifier)
                .values()
                .stream()
                .filter(Upgrader.class::isInstance)
                .map(Upgrader.class::cast)
                .sorted(Comparator.comparing(Upgrader::getOrder))
                .forEach(this::check);
    }

    private void check(Upgrader upgrader) {
        final String name = upgrader.getClass().getSimpleName();
        try {
            if (upgraderRepository.findById(upgrader.identifier()).isEmpty().blockingGet()) {
                log.warn("{} has not been applied (id={}) and upgraders are disabled on this node (upgrade.mode=false). Run the upgrader with upgrade.mode=true, or start a node with upgrade.mode unset.",
                        name, upgrader.identifier());
            }
        } catch (Exception e) {
            log.warn("Unable to check whether {} has been applied", name, e);
        }
    }
}
