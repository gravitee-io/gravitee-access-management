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

import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.node.api.notifier.NotificationAcknowledge;
import io.gravitee.node.api.notifier.NotificationAcknowledgeRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class NotificationAcknowledgeRepositoryTest extends AbstractManagementTest {

    @Autowired
    private NotificationAcknowledgeRepository repository;

    @Test
    public void testFindById() throws TechnicalException {
        NotificationAcknowledge acknowledge = new NotificationAcknowledge();
        acknowledge.setId("testid");
        acknowledge.setType("email-notifier");
        acknowledge.setResourceId("resource");
        acknowledge.setAudienceId("audience");
        acknowledge.setCreatedAt(new Date());
        acknowledge.setUpdatedAt(new Date());
        acknowledge.setResourceType("resource_type");
        acknowledge.setCounter(1);
        repository.create(acknowledge).blockingGet();

        TestObserver<NotificationAcknowledge> testObserver = repository.findById(acknowledge.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getId().equals(acknowledge.getId()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        NotificationAcknowledge acknowledge = new NotificationAcknowledge();
        acknowledge.setId("testid");
        acknowledge.setType("email-notifier");
        acknowledge.setResourceId("resource");
        acknowledge.setAudienceId("audience");
        acknowledge.setCreatedAt(new Date());
        acknowledge.setUpdatedAt(new Date());
        acknowledge.setResourceType("resource_type");
        acknowledge.setCounter(1);
        repository.create(acknowledge).blockingGet();

        TestObserver<NotificationAcknowledge> testObserver = repository.findById(acknowledge.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getId().equals(acknowledge.getId()) && d.getCounter() == 1);

        // increment
        acknowledge.incrementCounter();

        testObserver = repository.update(acknowledge).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getId().equals(acknowledge.getId()) && d.getCounter() == 2);

        // retrieve by ID to confirm the update has been done
        testObserver = repository.findById(acknowledge.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getId().equals(acknowledge.getId()) && d.getCounter() == 2);

    }

    @Test
    public void testFindByResourceIdAndTypeAndAudienceId() throws TechnicalException {
        NotificationAcknowledge acknowledge = new NotificationAcknowledge();
        acknowledge.setId("testid");
        acknowledge.setType("email-notifier");
        acknowledge.setResourceId("resource");
        acknowledge.setAudienceId("audience");
        acknowledge.setCreatedAt(new Date());
        acknowledge.setUpdatedAt(new Date());
        acknowledge.setResourceType("resource_type");
        acknowledge.setCounter(1);
        repository.create(acknowledge).blockingGet();

        TestObserver<NotificationAcknowledge> testObserver = repository.findByResourceIdAndTypeAndAudienceId(acknowledge.getResourceId(), acknowledge.getType(), acknowledge.getAudienceId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getId().equals(acknowledge.getId()));
    }

    @Test
    public void testNotFound() throws TechnicalException {
        final TestObserver<NotificationAcknowledge> test = repository.findByResourceIdAndTypeAndAudienceId("unknown", "unknown", "unknonwn").test();
        test.awaitTerminalEvent();
        test.assertNoValues();
    }

    @Test
    public void testDelete() throws TechnicalException {
        NotificationAcknowledge acknowledge = new NotificationAcknowledge();
        acknowledge.setId("testid");
        acknowledge.setType("email-notifier");
        acknowledge.setResourceId("resource");
        acknowledge.setAudienceId("audience");
        acknowledge.setCreatedAt(new Date());
        acknowledge.setUpdatedAt(new Date());
        acknowledge.setResourceType("resource_type");
        acknowledge.setCounter(1);
        repository.create(acknowledge).blockingGet();

        TestObserver<NotificationAcknowledge> testObserver = repository.findById(acknowledge.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getId().equals(acknowledge.getId()));

        final TestObserver<Void> test = repository.deleteByResourceId(acknowledge.getResourceId()).test();
        test.awaitTerminalEvent();
        test.assertNoErrors();

        testObserver = repository.findById(acknowledge.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoValues();
    }
}
