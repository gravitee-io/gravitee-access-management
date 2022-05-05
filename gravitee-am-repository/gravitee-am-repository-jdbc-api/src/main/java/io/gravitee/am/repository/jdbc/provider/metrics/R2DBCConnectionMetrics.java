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
package io.gravitee.am.repository.jdbc.provider.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.r2dbc.pool.ConnectionPool;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class R2DBCConnectionMetrics {

    private final MeterRegistry registry;

    private final Tags tags;

    public R2DBCConnectionMetrics(MeterRegistry registry, Tags tags) {
        this.registry = registry;
        this.tags = tags;
    }

    public void register(ConnectionPool pool) {
        pool.getMetrics().ifPresent(metrics -> {
            Gauge.builder("r2dbc_pool_acquiredSize", () -> metrics.acquiredSize()).tags(tags).register(registry);
            Gauge.builder("r2dbc_pool_allocatedSize", () -> metrics.allocatedSize()).tags(tags).register(registry);
            Gauge.builder("r2dbc_pool_pendingAcquireSize", () -> metrics.pendingAcquireSize()).tags(tags).register(registry);
            Gauge.builder("r2dbc_pool_idleSize", () -> metrics.idleSize()).tags(tags).register(registry);
            Gauge.builder("r2dbc_pool_maxAllocatedSize", () -> metrics.getMaxAllocatedSize()).tags(tags).register(registry);
        });
    }
}
