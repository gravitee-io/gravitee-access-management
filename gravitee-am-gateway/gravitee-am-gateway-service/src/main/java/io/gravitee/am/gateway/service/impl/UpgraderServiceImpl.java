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
package io.gravitee.am.gateway.service.impl;

import io.gravitee.am.gateway.service.InitializerService;
import io.gravitee.am.gateway.service.impl.upgrades.Upgrader;
import io.gravitee.common.service.AbstractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class UpgraderServiceImpl extends AbstractService<UpgraderServiceImpl> implements
        InitializerService<UpgraderServiceImpl> {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(UpgraderServiceImpl.class);

    @Override
    protected String name() {
        return "Upgrader service";
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        Map<String, Upgrader> upgraderBeans = applicationContext.getBeansOfType(Upgrader.class);
        upgraderBeans.values().forEach(new Consumer<Upgrader>() {
            @Override
            public void accept(Upgrader upgrader) {
                logger.info("Running upgrader bean {}", upgrader.getClass().getName());
                upgrader.upgrade();
            }
        });
    }
}
