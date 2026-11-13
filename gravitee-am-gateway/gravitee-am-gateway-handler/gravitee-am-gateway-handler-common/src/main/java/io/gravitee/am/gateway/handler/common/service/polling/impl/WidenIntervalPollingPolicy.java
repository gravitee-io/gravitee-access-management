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
package io.gravitee.am.gateway.handler.common.service.polling.impl;

import io.gravitee.am.common.polling.PollingRequest;
import io.gravitee.am.gateway.handler.common.service.polling.PollingPolicy;

public class WidenIntervalPollingPolicy<R extends PollingRequest> implements PollingPolicy<R> {

    static final int SLOW_DOWN_INCREMENT_IN_SEC = 5;

    static final int MAX_INTERVAL_IN_SEC = 60;

    @Override
    public int effectiveIntervalInSec(R request, int intervalInSec) {
        return Math.min(intervalInSec + request.getSlowDownCount() * SLOW_DOWN_INCREMENT_IN_SEC, MAX_INTERVAL_IN_SEC);
    }
}
