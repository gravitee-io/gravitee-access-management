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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.model.alert.AlertTriggerType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.am.repository.management.api.search.AlertTriggerCriteria;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AlertTriggerRepositoryTest extends AbstractManagementTest {

    private static final String DOMAIN_ID = "domain#1";
    private static final String NOTIFIER_ID1 = "notifier#1";
    private static final String NOTIFIER_ID2 = "notifier#2";

    @Autowired
    private AlertTriggerRepository alertTriggerRepository;

    @Test
    public void testFindById() {
        // create idp
        AlertTrigger alertTrigger = buildAlertTrigger();
        AlertTrigger alertTriggerCreated = alertTriggerRepository.create(alertTrigger).blockingGet();

        // fetch idp
        TestObserver<AlertTrigger> testObserver = alertTriggerRepository.findById(alertTriggerCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(found -> found.getId().equals(alertTrigger.getId()) && found.getAlertNotifiers().size() == 2);
    }

    @Test
    public void testNotFoundById() {
        alertTriggerRepository.findById("UNKNOWN").test().assertEmpty();
    }

    @Test
    public void testCreate() {
        AlertTrigger alertTrigger = buildAlertTrigger();
        TestObserver<AlertTrigger> testObserver = alertTriggerRepository.create(alertTrigger).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idp -> idp.getId().equals(alertTrigger.getId()));
    }

    @Test
    public void testUpdate() {
        // create idp
        AlertTrigger alertTrigger = buildAlertTrigger();
        AlertTrigger alertTriggerCreated = alertTriggerRepository.create(alertTrigger).blockingGet();

        // update idp
        AlertTrigger updatedAlertTrigger = buildAlertTrigger();
        updatedAlertTrigger.setId(alertTriggerCreated.getId());
        updatedAlertTrigger.setEnabled(false);

        TestObserver<AlertTrigger> testObserver = alertTriggerRepository.update(updatedAlertTrigger).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(updated -> updated.getId().equals(updatedAlertTrigger.getId())
                && !updated.isEnabled());
    }

    @Test
    public void testDelete() {
        // create idp
        AlertTrigger alertTrigger = buildAlertTrigger();
        AlertTrigger alertTriggerCreated = alertTriggerRepository.create(alertTrigger).blockingGet();

        // delete idp
        TestObserver<Void> testObserver1 = alertTriggerRepository.delete(alertTriggerCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch idp
        alertTriggerRepository.findById(alertTriggerCreated.getId()).test().assertEmpty();
    }

    @Test
    public void findByCriteria_alertNotifiers() {
        AlertTrigger alertTriggerToCreate = buildAlertTrigger();
        alertTriggerToCreate.setAlertNotifiers(Collections.emptyList());
        alertTriggerRepository.create(alertTriggerToCreate).blockingGet();

        alertTriggerToCreate = buildAlertTrigger();
        AlertTrigger alertTriggerCreated = alertTriggerRepository.create(alertTriggerToCreate).blockingGet();

        AlertTriggerCriteria criteria = new AlertTriggerCriteria();
        criteria.setEnabled(false);
        criteria.setAlertNotifierIds(Collections.singletonList(NOTIFIER_ID1));
        TestSubscriber<AlertTrigger> testObserver1 = alertTriggerRepository.findByCriteria(ReferenceType.DOMAIN, DOMAIN_ID, criteria).test();

        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertNoValues();

        alertTriggerCreated.setEnabled(false);
        final AlertTrigger alertTriggerUpdated = alertTriggerRepository.update(alertTriggerCreated).blockingGet();
        testObserver1 = alertTriggerRepository.findByCriteria(ReferenceType.DOMAIN, DOMAIN_ID, criteria).test();
        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValue(alertTrigger -> alertTrigger.getId().equals(alertTriggerUpdated.getId()));
    }

    @Test
    public void findByCriteria_type() {
        AlertTrigger alertTriggerToCreate = buildAlertTrigger();
        AlertTrigger alertTriggerCreated = alertTriggerRepository.create(alertTriggerToCreate).blockingGet();

        AlertTriggerCriteria criteria = new AlertTriggerCriteria();
        criteria.setEnabled(true);
        criteria.setType(AlertTriggerType.TOO_MANY_LOGIN_FAILURES);
        TestSubscriber<AlertTrigger> testObserver1 = alertTriggerRepository.findByCriteria(ReferenceType.DOMAIN, DOMAIN_ID, criteria).test();

        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValue(alertTrigger -> alertTrigger.getId().equals(alertTriggerCreated.getId()));
    }

    @Test
    public void findAll() {
        TestSubscriber<AlertTrigger> testObserver1 = alertTriggerRepository.findAll(ReferenceType.DOMAIN, DOMAIN_ID).test();

        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertNoValues();
        AlertTrigger alertTriggerToCreate1 = buildAlertTrigger();
        AlertTrigger alertTriggerToCreate2 = buildAlertTrigger();
        alertTriggerToCreate2.setReferenceId("domain#2");
        AlertTrigger alertTriggerCreated1 = alertTriggerRepository.create(alertTriggerToCreate1).blockingGet();
        alertTriggerRepository.create(alertTriggerToCreate2).blockingGet();

        testObserver1 = alertTriggerRepository.findAll(ReferenceType.DOMAIN, DOMAIN_ID).test();

        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertValue(alertTrigger -> alertTrigger.getId().equals(alertTriggerCreated1.getId()));
    }

    @Test
    public void findByCriteriaWithEmptyAlertNotifierIdList() {
        TestSubscriber<AlertTrigger> testObserver1 = alertTriggerRepository.findAll(ReferenceType.DOMAIN, DOMAIN_ID).test();

        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertNoValues();
        AlertTrigger alertTriggerToCreate1 = buildAlertTrigger();
        AlertTrigger alertTriggerToCreate2 = buildAlertTrigger();
        alertTriggerToCreate2.setReferenceId("domain#2");
        AlertTrigger alertTriggerCreated1 = alertTriggerRepository.create(alertTriggerToCreate1).blockingGet();
        alertTriggerRepository.create(alertTriggerToCreate2).blockingGet();

        final AlertTriggerCriteria criteria = new AlertTriggerCriteria();
        criteria.setAlertNotifierIds(Collections.emptyList());
        testObserver1 = alertTriggerRepository.findByCriteria(ReferenceType.DOMAIN, DOMAIN_ID, criteria).test();

        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertValue(alertTrigger -> alertTrigger.getId().equals(alertTriggerCreated1.getId()));
    }


    private AlertTrigger buildAlertTrigger() {
        AlertTrigger alertTrigger = new AlertTrigger();
        alertTrigger.setId(RandomString.generate());
        alertTrigger.setEnabled(true);
        alertTrigger.setType(AlertTriggerType.TOO_MANY_LOGIN_FAILURES);
        alertTrigger.setReferenceType(ReferenceType.DOMAIN);
        alertTrigger.setReferenceId(DOMAIN_ID);
        alertTrigger.setAlertNotifiers(Arrays.asList(NOTIFIER_ID1, NOTIFIER_ID2));
        alertTrigger.setCreatedAt(new Date());
        alertTrigger.setUpdatedAt(new Date());
        return alertTrigger;
    }
}
