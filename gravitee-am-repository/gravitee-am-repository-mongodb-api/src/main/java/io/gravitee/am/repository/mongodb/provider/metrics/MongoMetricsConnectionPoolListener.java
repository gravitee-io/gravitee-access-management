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
package io.gravitee.am.repository.mongodb.provider.metrics;

import com.mongodb.connection.ServerId;
import com.mongodb.event.ConnectionCheckOutFailedEvent;
import com.mongodb.event.ConnectionCheckOutStartedEvent;
import com.mongodb.event.ConnectionCheckedInEvent;
import com.mongodb.event.ConnectionCheckedOutEvent;
import com.mongodb.event.ConnectionClosedEvent;
import com.mongodb.event.ConnectionCreatedEvent;
import com.mongodb.event.ConnectionPoolClosedEvent;
import com.mongodb.event.ConnectionPoolCreatedEvent;
import com.mongodb.event.ConnectionPoolListener;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copy and adaptation of https://github.com/micrometer-metrics/micrometer/blob/main/micrometer-core/src/main/java/io/micrometer/core/instrument/binder/mongodb/MongoMetricsConnectionPoolListener.java
 * because for some reason, the micrometer implementation throws a ClassNotFound
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoMetricsConnectionPoolListener implements ConnectionPoolListener {

    private static final String METRIC_PREFIX = "mongodb.driver.pool.";

    private final Map<ServerId, AtomicInteger> poolSizes = new ConcurrentHashMap<>();

    private final Map<ServerId, AtomicInteger> checkedOutCounts = new ConcurrentHashMap<>();

    private final Map<ServerId, AtomicInteger> waitQueueSizes = new ConcurrentHashMap<>();

    private final Map<ServerId, List<Meter>> meters = new ConcurrentHashMap<>();

    private final MeterRegistry registry;

    private final String metricsTag;

    /**
     * Create a new {@code MongoMetricsConnectionPoolListener}.
     * @param registry registry to use
     */
    public MongoMetricsConnectionPoolListener(MeterRegistry registry, String metricsTag) {
        this.registry = registry;
        this.metricsTag = metricsTag;
    }

    @Override
    public void connectionPoolCreated(ConnectionPoolCreatedEvent event) {
        List<Meter> connectionMeters = new ArrayList<>();
        connectionMeters.add(registerGauge(event, METRIC_PREFIX + "size",
                "the current size of the connection pool, including idle and and in-use members", poolSizes));
        connectionMeters.add(registerGauge(event, METRIC_PREFIX + "checkedout",
                "the count of connections that are currently in use", checkedOutCounts));
        connectionMeters.add(registerGauge(event, METRIC_PREFIX + "waitqueuesize",
                "the current size of the wait queue for a connection from the pool", waitQueueSizes));
        meters.put(event.getServerId(), connectionMeters);
    }

    @Override
    public void connectionPoolClosed(ConnectionPoolClosedEvent event) {
        ServerId serverId = event.getServerId();
        for (Meter meter : meters.get(serverId)) {
            registry.remove(meter);
        }
        meters.remove(serverId);
        poolSizes.remove(serverId);
        checkedOutCounts.remove(serverId);
        waitQueueSizes.remove(serverId);
    }

    @Override
    public void connectionCheckOutStarted(ConnectionCheckOutStartedEvent event) {
        AtomicInteger waitQueueSize = waitQueueSizes.get(event.getServerId());
        if (waitQueueSize != null) {
            waitQueueSize.incrementAndGet();
        }
    }

    @Override
    public void connectionCheckedOut(ConnectionCheckedOutEvent event) {
        AtomicInteger checkedOutCount = checkedOutCounts.get(event.getConnectionId().getServerId());
        if (checkedOutCount != null) {
            checkedOutCount.incrementAndGet();
        }

        AtomicInteger waitQueueSize = waitQueueSizes.get(event.getConnectionId().getServerId());
        if (waitQueueSize != null) {
            waitQueueSize.decrementAndGet();
        }
    }

    @Override
    public void connectionCheckOutFailed(ConnectionCheckOutFailedEvent event) {
        AtomicInteger waitQueueSize = waitQueueSizes.get(event.getServerId());
        if (waitQueueSize != null) {
            waitQueueSize.decrementAndGet();
        }
    }

    @Override
    public void connectionCheckedIn(ConnectionCheckedInEvent event) {
        AtomicInteger checkedOutCount = checkedOutCounts.get(event.getConnectionId().getServerId());
        if (checkedOutCount != null) {
            checkedOutCount.decrementAndGet();
        }
    }

    @Override
    public void connectionCreated(ConnectionCreatedEvent event) {
        AtomicInteger poolSize = poolSizes.get(event.getConnectionId().getServerId());
        if (poolSize != null) {
            poolSize.incrementAndGet();
        }
    }

    @Override
    public void connectionClosed(ConnectionClosedEvent event) {
        AtomicInteger poolSize = poolSizes.get(event.getConnectionId().getServerId());
        if (poolSize != null) {
            poolSize.decrementAndGet();
        }
    }

    private Gauge registerGauge(ConnectionPoolCreatedEvent event, String metricName, String description,
                                Map<ServerId, AtomicInteger> metrics) {
        AtomicInteger value = new AtomicInteger();
        metrics.put(event.getServerId(), value);
        return Gauge.builder(metricName, value, AtomicInteger::doubleValue).description(description)
                .tags(connectionPoolTags(event)).register(registry);
    }

    public Iterable<Tag> connectionPoolTags(final ConnectionPoolCreatedEvent event) {
        return Tags.of(Tag.of("cluster.id", event.getServerId().getClusterId().getValue()),
                Tag.of("server.address", event.getServerId().getAddress().toString()),
                Tag.of("pool", metricsTag)
        );
    }
}
