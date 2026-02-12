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
package io.gravitee.am.repository.gateway.api;

import io.gravitee.am.model.ActionLease;
import io.gravitee.am.repository.gateway.AbstractGatewayTest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ActionLeaseRepositoryTest extends AbstractGatewayTest {

    @Autowired
    protected ActionLeaseRepository repository;

    @Test
    public void shouldAcquireNewLease() {
        String action = "test-action-new";
        String nodeId = "node-1";
        Duration duration = Duration.ofMinutes(5);

        TestObserver<ActionLease> observer = repository.acquireLease(action, nodeId, duration).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(lease -> lease.getAction().equals(action));
        observer.assertValue(lease -> lease.getNodeId().equals(nodeId));
        observer.assertValue(lease -> lease.getExpiryDate() != null);
        observer.assertValue(lease -> lease.getId() != null);
    }

    @Test
    public void shouldNotAcquireLeaseHeldByAnotherNode() {
        String action = "test-action-held";
        String nodeId1 = "node-1";
        String nodeId2 = "node-2";
        Duration duration = Duration.ofMinutes(5);

        // Node 1 acquires the lease
        TestObserver<ActionLease> observer1 = repository.acquireLease(action, nodeId1, duration).test();
        observer1.awaitDone(10, TimeUnit.SECONDS);
        observer1.assertNoErrors();
        observer1.assertComplete();
        observer1.assertValue(lease -> lease.getNodeId().equals(nodeId1));

        // Node 2 tries to acquire the same lease - should fail
        TestObserver<ActionLease> observer2 = repository.acquireLease(action, nodeId2, duration).test();
        observer2.awaitDone(10, TimeUnit.SECONDS);
        observer2.assertNoErrors();
        observer2.assertNoValues();
    }

    @Test
    public void shouldReacquireLeaseByTheSameNode() {
        String action = "test-action-reacquire";
        String nodeId = "node-1";
        Duration duration = Duration.ofMinutes(5);

        // Node acquires the lease
        TestObserver<ActionLease> observer1 = repository.acquireLease(action, nodeId, duration).test();
        observer1.awaitDone(10, TimeUnit.SECONDS);
        observer1.assertNoErrors();
        observer1.assertValue(lease -> lease.getNodeId().equals(nodeId));

        // Same node reacquires the lease - should succeed
        TestObserver<ActionLease> observer2 = repository.acquireLease(action, nodeId, duration).test();
        observer2.awaitDone(10, TimeUnit.SECONDS);
        observer2.assertNoErrors();
        observer2.assertComplete();
        observer2.assertValue(lease -> lease.getNodeId().equals(nodeId));
    }

    @Test
    public void shouldAcquireExpiredLease() throws InterruptedException {
        String action = "test-action-expired";
        String nodeId1 = "node-1";
        String nodeId2 = "node-2";
        Duration shortDuration = Duration.ofMillis(500);

        // Node 1 acquires the lease with short duration
        TestObserver<ActionLease> observer1 = repository.acquireLease(action, nodeId1, shortDuration).test();
        observer1.awaitDone(10, TimeUnit.SECONDS);
        observer1.assertNoErrors();
        observer1.assertValue(lease -> lease.getNodeId().equals(nodeId1));

        // Wait for the lease to expire
        Thread.sleep(1000);

        // Node 2 should now be able to acquire the expired lease
        TestObserver<ActionLease> observer2 = repository.acquireLease(action, nodeId2, Duration.ofMinutes(5)).test();
        observer2.awaitDone(10, TimeUnit.SECONDS);
        observer2.assertNoErrors();
        observer2.assertComplete();
        observer2.assertValue(lease -> lease.getNodeId().equals(nodeId2));
    }

    @Test
    public void shouldReleaseLease() {
        String action = "test-action-release";
        String nodeId = "node-1";
        Duration duration = Duration.ofMinutes(5);

        // Node acquires the lease
        TestObserver<ActionLease> observer1 = repository.acquireLease(action, nodeId, duration).test();
        observer1.awaitDone(10, TimeUnit.SECONDS);
        observer1.assertNoErrors();
        observer1.assertValue(lease -> lease.getNodeId().equals(nodeId));

        // Node releases the lease
        TestObserver<Void> releaseObserver = repository.releaseLease(action, nodeId).test();
        releaseObserver.awaitDone(10, TimeUnit.SECONDS);
        releaseObserver.assertNoErrors();
        releaseObserver.assertComplete();

        // Another node should now be able to acquire the lease
        String nodeId2 = "node-2";
        TestObserver<ActionLease> observer2 = repository.acquireLease(action, nodeId2, duration).test();
        observer2.awaitDone(10, TimeUnit.SECONDS);
        observer2.assertNoErrors();
        observer2.assertComplete();
        observer2.assertValue(lease -> lease.getNodeId().equals(nodeId2));
    }

    @Test
    public void shouldReleaseOnlyOwnLease() {
        String action = "test-action-release-own";
        String nodeId1 = "node-1";
        String nodeId2 = "node-2";
        Duration duration = Duration.ofMinutes(5);

        // Node 1 acquires the lease
        TestObserver<ActionLease> observer1 = repository.acquireLease(action, nodeId1, duration).test();
        observer1.awaitDone(10, TimeUnit.SECONDS);
        observer1.assertNoErrors();
        observer1.assertValue(lease -> lease.getNodeId().equals(nodeId1));

        // Node 2 tries to release the lease - should not affect node 1's lease
        TestObserver<Void> releaseObserver = repository.releaseLease(action, nodeId2).test();
        releaseObserver.awaitDone(10, TimeUnit.SECONDS);
        releaseObserver.assertNoErrors();

        // Node 2 still cannot acquire the lease
        TestObserver<ActionLease> observer2 = repository.acquireLease(action, nodeId2, duration).test();
        observer2.awaitDone(10, TimeUnit.SECONDS);
        observer2.assertNoErrors();
        observer2.assertNoValues();

        // Node 1 can still reacquire
        TestObserver<ActionLease> observer3 = repository.acquireLease(action, nodeId1, duration).test();
        observer3.awaitDone(10, TimeUnit.SECONDS);
        observer3.assertNoErrors();
        observer3.assertComplete();
        observer3.assertValue(lease -> lease.getNodeId().equals(nodeId1));
    }

    @Test
    public void shouldHandleMultipleActionsIndependently() {
        String action1 = "test-action-1";
        String action2 = "test-action-2";
        String nodeId1 = "node-1";
        String nodeId2 = "node-2";
        Duration duration = Duration.ofMinutes(5);

        // Node 1 acquires lease for action1
        TestObserver<ActionLease> observer1 = repository.acquireLease(action1, nodeId1, duration).test();
        observer1.awaitDone(10, TimeUnit.SECONDS);
        observer1.assertNoErrors();
        observer1.assertValue(lease -> lease.getAction().equals(action1));
        observer1.assertValue(lease -> lease.getNodeId().equals(nodeId1));

        // Node 2 acquires lease for action2
        TestObserver<ActionLease> observer2 = repository.acquireLease(action2, nodeId2, duration).test();
        observer2.awaitDone(10, TimeUnit.SECONDS);
        observer2.assertNoErrors();
        observer2.assertValue(lease -> lease.getAction().equals(action2));
        observer2.assertValue(lease -> lease.getNodeId().equals(nodeId2));

        // Node 2 cannot acquire lease for action1
        TestObserver<ActionLease> observer3 = repository.acquireLease(action1, nodeId2, duration).test();
        observer3.awaitDone(10, TimeUnit.SECONDS);
        observer3.assertNoErrors();
        observer3.assertNoValues();

        // Node 1 cannot acquire lease for action2
        TestObserver<ActionLease> observer4 = repository.acquireLease(action2, nodeId1, duration).test();
        observer4.awaitDone(10, TimeUnit.SECONDS);
        observer4.assertNoErrors();
        observer4.assertNoValues();
    }

    @Test
    public void shouldUpdateExpiryDateWhenReacquiring() throws InterruptedException {
        String action = "test-action-update-expiry";
        String nodeId = "node-1";
        Duration shortDuration = Duration.ofSeconds(2);
        Duration longDuration = Duration.ofMinutes(5);

        // Node acquires the lease with short duration
        TestObserver<ActionLease> observer1 = repository.acquireLease(action, nodeId, shortDuration).test();
        observer1.awaitDone(10, TimeUnit.SECONDS);
        observer1.assertNoErrors();
        ActionLease firstLease = observer1.values().get(0);

        // Wait a bit
        Thread.sleep(500);

        // Same node reacquires with longer duration
        TestObserver<ActionLease> observer2 = repository.acquireLease(action, nodeId, longDuration).test();
        observer2.awaitDone(10, TimeUnit.SECONDS);
        observer2.assertNoErrors();
        ActionLease secondLease = observer2.values().get(0);

        // Expiry date should be updated
        observer2.assertValue(lease -> lease.getExpiryDate().after(firstLease.getExpiryDate()));
    }

    @Test
    public void shouldReleaseNonExistentLeaseWithoutError() {
        String action = "test-action-nonexistent";
        String nodeId = "node-1";

        // Try to release a lease that doesn't exist
        TestObserver<Void> releaseObserver = repository.releaseLease(action, nodeId).test();
        releaseObserver.awaitDone(10, TimeUnit.SECONDS);
        releaseObserver.assertNoErrors();
        releaseObserver.assertComplete();
    }
}
