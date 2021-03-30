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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.node.api.Monitoring;
import io.gravitee.node.api.NodeMonitoringRepository;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.Assert.assertTrue;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class NodeMonitoringRepositoryTest extends AbstractManagementTest {

    private static final String NODE_ID = "node#1";
    private static final String MONITORING_TYPE = "MONITOR";

    @Autowired
    private NodeMonitoringRepository nodeMonitoringRepository;

    @Test
    public void testCreate() {
        Monitoring alertNotifier = buildMonitoring();
        TestObserver<Monitoring> testObserver = nodeMonitoringRepository.create(alertNotifier).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(monitoring -> monitoring.getId().equals(alertNotifier.getId()));
    }

    @Test
    public void testUpdate() {
        // create idp
        Monitoring monitoring = buildMonitoring();
        Monitoring monitoringCreated = nodeMonitoringRepository.create(monitoring).blockingGet();

        // update idp
        Monitoring updatedMonitoring = buildMonitoring();
        updatedMonitoring.setId(monitoringCreated.getId());
        updatedMonitoring.setPayload("updatedPayload");

        TestObserver<Monitoring> testObserver = nodeMonitoringRepository.update(updatedMonitoring).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(updated -> updated.getId().equals(updatedMonitoring.getId())
                && updated.getPayload().equals(updatedMonitoring.getPayload()));
    }

    @Test
    public void findByNodeIdAndType() {

        Monitoring monitoringToCreate;

        for (int i = 0; i < 10; i++) {
            monitoringToCreate = buildMonitoring();
            monitoringToCreate.setType((i % 2 == 0) ? MONITORING_TYPE : "HEALTH_CHECK");
            nodeMonitoringRepository.create(monitoringToCreate).blockingGet();
        }

        monitoringToCreate = buildMonitoring();
        monitoringToCreate.setNodeId(NODE_ID);
        monitoringToCreate.setType(MONITORING_TYPE);
        final Monitoring monitoringCreated = nodeMonitoringRepository.create(monitoringToCreate).blockingGet();

        TestObserver<Monitoring> obs = nodeMonitoringRepository.findByNodeIdAndType(NODE_ID, MONITORING_TYPE).test();
        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(monitoring -> monitoring.getId().equals(monitoringCreated.getId()));
    }

    @Test
    public void findByTypeAndTimeFrame() {

        long from = LocalDateTime.of(1970, 12, 25, 10, 0, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
        long to = LocalDateTime.of(1970, 12, 25, 11, 0, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
        Monitoring monitoringToCreate;

        // Create 10 monitoring objects.
        for (int i = 0; i < 10; i++) {
            monitoringToCreate = buildMonitoring();
            if (i % 2 == 0) {
                // Change updated date 1 out of 2.
                monitoringToCreate.setUpdatedAt(new Date(from + 10000));
            }

            if (i == 8) {
                // Change the type of the 9th, so it should be excluded from the results.
                monitoringToCreate.setType("HEALTH_CHECK");
            }

            nodeMonitoringRepository.create(monitoringToCreate).blockingGet();
        }

        TestSubscriber<Monitoring> obs = nodeMonitoringRepository.findByTypeAndTimeFrame(MONITORING_TYPE, from, to).test();
        obs.awaitTerminalEvent();
        obs.assertValueCount(4);

        obs.values().forEach(monitoring -> {
            assertTrue(monitoring.getUpdatedAt().getTime() >= from
                    && monitoring.getUpdatedAt().getTime() <= to
                    && monitoring.getType().equals(MONITORING_TYPE));
        });

    }

    private Monitoring buildMonitoring() {
        Monitoring monitoring = new Monitoring();
        monitoring.setId(RandomString.generate());
        monitoring.setNodeId(RandomString.generate());
        monitoring.setPayload("payload");
        monitoring.setType(MONITORING_TYPE);
        monitoring.setCreatedAt(new Date());
        monitoring.setEvaluatedAt(new Date());
        monitoring.setUpdatedAt(new Date());
        return monitoring;
    }
}
