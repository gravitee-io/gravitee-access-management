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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.notification.UserNotification;
import io.gravitee.am.model.notification.UserNotificationStatus;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.UUID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserNotificationRepositoryTest extends AbstractManagementTest {

    @Autowired
    private UserNotificationRepository repository;

    @Test
    public void testFindByAudienceAndStatus() throws TechnicalException {
        // Two unread notifications
        UserNotification userNotification = createUserNotification();
        userNotification.setAudienceId("audid");
        userNotification.setStatus(UserNotificationStatus.UNREAD);
        repository.create(userNotification).blockingGet();

        userNotification = createUserNotification();
        userNotification.setAudienceId("audid");
        userNotification.setStatus(UserNotificationStatus.UNREAD);
        repository.create(userNotification).blockingGet();

        // next notification mut be ignored by the search (invalid status or audience)
        userNotification = createUserNotification();
        userNotification.setAudienceId("audid");
        userNotification.setStatus(UserNotificationStatus.READ);
        repository.create(userNotification).blockingGet();

        userNotification = createUserNotification();
        userNotification.setAudienceId("otheraudid");
        userNotification.setStatus(UserNotificationStatus.UNREAD);
        repository.create(userNotification).blockingGet();

        final TestSubscriber<UserNotification> testSubscriber = repository.findAllByAudienceAndStatus("audid", UserNotificationStatus.UNREAD).test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(2);
    }


    @Test
    public void testMarkAsRead() throws TechnicalException {
        UserNotification userNotification = createUserNotification();
        UserNotification createdNotification = repository.create(userNotification).blockingGet();

        TestSubscriber<UserNotification> testSubscriber = repository.findAllByAudienceAndStatus(createdNotification.getAudienceId(), UserNotificationStatus.UNREAD).test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);

        final TestObserver<Void> update = repository.updateNotificationStatus(createdNotification.getId(), UserNotificationStatus.READ).test();
        update.awaitTerminalEvent();
        update.assertNoErrors();

        testSubscriber = repository.findAllByAudienceAndStatus(createdNotification.getAudienceId(), UserNotificationStatus.UNREAD).test();
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(0);

        testSubscriber = repository.findAllByAudienceAndStatus(createdNotification.getAudienceId(), UserNotificationStatus.READ).test();
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        UserNotification userNotification = createUserNotification();
        UserNotification notification = repository.create(userNotification).blockingGet();

        TestObserver<UserNotification> testObserver = repository.findById(notification.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEquals(notification, testObserver);
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        final TestObserver<UserNotification> test = repository.findById("test").test();
        test.awaitTerminalEvent();
        test.assertNoValues();
        test.assertNoErrors();
    }

    @Test
    public void testCreate() throws TechnicalException {
        UserNotification userNotification = createUserNotification();

        TestObserver<UserNotification> testObserver = repository.create(userNotification).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEquals(userNotification, testObserver);
    }

    private void assertEquals(UserNotification userNotification, TestObserver<UserNotification> testObserver) {
        testObserver.assertValue(userNotifCreated -> userNotifCreated.getAudienceId().equals(userNotification.getAudienceId())
                        &&  userNotifCreated.getReferenceType().equals(userNotification.getReferenceType())
                        &&  userNotifCreated.getReferenceId().equals(userNotification.getReferenceId())
                        &&  userNotifCreated.getMessage().equals(userNotification.getMessage())
                );
    }

    private UserNotification createUserNotification() {
        // create userNotification
        UserNotification userNotification = new UserNotification();
        userNotification.setAudienceId(UUID.randomUUID().toString());
        userNotification.setStatus(UserNotificationStatus.UNREAD);
        userNotification.setMessage("message-" + UUID.randomUUID().toString());
        userNotification.setReferenceType(ReferenceType.DOMAIN);
        userNotification.setReferenceId(UUID.randomUUID().toString());
        userNotification.setCreatedAt(new Date());
        userNotification.setUpdatedAt(new Date());
        return userNotification;
    }

    @Test
    public void testUpdate() throws TechnicalException {
        UserNotification userNotification = createUserNotification();
        UserNotification notification = repository.create(userNotification).blockingGet();

        UserNotification updatedTag = createUserNotification();
        updatedTag.setId(notification.getId());
        updatedTag.setStatus(UserNotificationStatus.READ);

        TestObserver<UserNotification> testObserver = repository.update(updatedTag).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEquals(updatedTag, testObserver);

    }

    @Test
    public void testDelete() throws TechnicalException {
        UserNotification userNotification = createUserNotification();
        UserNotification notification = repository.create(userNotification).blockingGet();



        TestObserver<UserNotification> testObserver = repository.findById(notification.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);

        // delete tag
        TestObserver testObserver1 = repository.delete(notification.getId()).test();
        testObserver1.awaitTerminalEvent();
        testObserver.assertNoErrors();

        // fetch tag
        final TestObserver<UserNotification> test = repository.findById(notification.getId()).test();
        test.awaitTerminalEvent();
        test.assertNoErrors();
        test.assertNoValues();
    }

}
