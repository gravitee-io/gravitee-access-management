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
package io.gravitee.am.management.service;

import io.gravitee.plugin.alert.AlertTriggerProviderManager;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AlertService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertService.class);

    private final AlertTriggerProviderManager triggerProviderManager;

    public AlertService(AlertTriggerProviderManager triggerProviderManager) {
        this.triggerProviderManager = triggerProviderManager;
    }

    /**
     * Indicates if alerting is available or not.
     * The alerting feature is available if there is at least one trigger provider registered (meaning AM is connected to alert engine).
     *
     * @return <code>true</code> if the alerting feature is available, <code>false</code> else.
     */
    public Single<Boolean> isAlertingAvailable() {
        LOGGER.debug("Get alert available status");

        return Single.just(!this.triggerProviderManager.findAll().isEmpty());
    }
}
