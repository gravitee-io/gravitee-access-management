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

package io.gravitee.am.management.service.impl.upgrades.system;

import io.gravitee.am.management.service.impl.upgrades.system.upgraders.SystemUpgrader;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.Node;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This service class execute upgraders which have to be executed everytimes to sync gravitee.yaml config to a system plugin config
 * For example, to be sure that the DB settings associated to the default IDP is updated if the DB parameters changes into the gravitee.yaml
 */
@Component
@Slf4j
public class SystemUpgraderService extends AbstractService<SystemUpgraderService> implements LifecycleComponent<SystemUpgraderService> {

    @Override
    protected String name() {
        return "System upgrader service";
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        Map<String, SystemUpgrader> sysUpgraderBeans = applicationContext.getBeansOfType(SystemUpgrader.class);

        AtomicBoolean stopUpgrade = new AtomicBoolean(false);
        sysUpgraderBeans
                .values()
                .stream()
                .sorted(Comparator.comparing(SystemUpgrader::getOrder))
                .forEach(upgrader -> {
                    try {
                        log.info("Apply {} ...", upgrader.identifier());
                        upgrader.upgrade().blockingAwait();
                    } catch (Exception ex) {
                        log.error("Unable to apply the system upgrader: {}", upgrader.identifier(), ex);
                        stopUpgrade.set(true);
                    }
                });

        if (stopUpgrade.get()) {
            Node node = applicationContext.getBean(Node.class);
            node.preStop();
            node.stop();
            node.postStop();
            log.error("Stopping because one of the system upgrades could not be performed");
            exitOnError();
        }
    }

    protected void exitOnError() {
        System.exit(1);
    }
}
