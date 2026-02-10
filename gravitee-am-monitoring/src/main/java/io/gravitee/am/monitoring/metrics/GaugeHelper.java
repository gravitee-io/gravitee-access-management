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
package io.gravitee.am.monitoring.metrics;

import io.gravitee.node.monitoring.metrics.Metrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GaugeHelper {

    private AtomicLong value = new AtomicLong(0);

    public GaugeHelper(String name) {
        this(name, Tags.empty());
    }

    public GaugeHelper(String name, Tags tags) {
        Gauge.builder(name, () -> this.value.get())
                .tags(tags)
                .register(Metrics.getDefaultRegistry());
    }

    public void updateValue(long value) {
        this.value.set(value);
    }

    public void incrementValue() {
        this.value.incrementAndGet();
    }

    public void decrementValue() {
        this.value.decrementAndGet();
    }

    public void decrementValue(int count) {
        this.value.updateAndGet(v -> v - count);
    }
}
