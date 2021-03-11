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
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.am.repository.management.api.search.AlertNotifierCriteria;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Date;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AlertNotifierRepositoryTest extends AbstractManagementTest {

    private static final String DOMAIN_ID = "alertNotifier#1";

    @Autowired
    private AlertNotifierRepository alertNotifierRepository;

    @Test
    public void testFindById() {
        // create idp
        AlertNotifier alertNotifier = buildAlertNotifier();
        AlertNotifier alertNotifierCreated = alertNotifierRepository.create(alertNotifier).blockingGet();

        // fetch idp
        TestObserver<AlertNotifier> testObserver = alertNotifierRepository.findById(alertNotifierCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(found -> found.getId().equals(alertNotifier.getId()));
    }

    @Test
    public void testNotFoundById() {
        alertNotifierRepository.findById("UNKNOWN").test().assertEmpty();
    }

    @Test
    public void testCreate() {
        AlertNotifier alertNotifier = buildAlertNotifier();
        TestObserver<AlertNotifier> testObserver = alertNotifierRepository.create(alertNotifier).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idp -> idp.getId().equals(alertNotifier.getId()));
    }

    @Test
    public void testUpdate() {
        // create idp
        AlertNotifier alertNotifier = buildAlertNotifier();
        AlertNotifier alertNotifierCreated = alertNotifierRepository.create(alertNotifier).blockingGet();

        // update idp
        AlertNotifier updatedAlertNotifier = buildAlertNotifier();
        updatedAlertNotifier.setId(alertNotifierCreated.getId());
        updatedAlertNotifier.setEnabled(false);

        TestObserver<AlertNotifier> testObserver = alertNotifierRepository.update(updatedAlertNotifier).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(updated -> updated.getId().equals(updatedAlertNotifier.getId())
                && !updated.isEnabled());
    }

    @Test
    public void testDelete() {
        // create idp
        AlertNotifier alertNotifier = buildAlertNotifier();
        AlertNotifier alertNotifierCreated = alertNotifierRepository.create(alertNotifier).blockingGet();

        // delete idp
        TestObserver<Void> testObserver1 = alertNotifierRepository.delete(alertNotifierCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch idp
        alertNotifierRepository.findById(alertNotifierCreated.getId()).test().assertEmpty();
    }

    @Test
    public void findByCriteria() {
        AlertNotifier alertNotifierToCreate = buildAlertNotifier();
        AlertNotifier alertNotifierCreated = alertNotifierRepository.create(alertNotifierToCreate).blockingGet();

        AlertNotifierCriteria criteria = new AlertNotifierCriteria();
        criteria.setEnabled(false);
        TestSubscriber<AlertNotifier> testObserver1 = alertNotifierRepository.findByCriteria(ReferenceType.DOMAIN, DOMAIN_ID, criteria).test();

        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertNoValues();

        alertNotifierCreated.setEnabled(false);
        final AlertNotifier alertNotifierUpdated = alertNotifierRepository.update(alertNotifierCreated).blockingGet();
        testObserver1 = alertNotifierRepository.findByCriteria(ReferenceType.DOMAIN, DOMAIN_ID, criteria).test();
        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValue(alertNotifier -> alertNotifier.getId().equals(alertNotifierUpdated.getId()));
    }

    @Test
    public void findByDomain() {
        AlertNotifier alertNotifierToCreate = buildAlertNotifier();
        AlertNotifier alertNotifierCreated = alertNotifierRepository.create(alertNotifierToCreate).blockingGet();

        TestSubscriber<AlertNotifier> testObserver1 = alertNotifierRepository.findByDomain(DOMAIN_ID).test();

        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValue(alertNotifier -> alertNotifier.getId().equals(alertNotifierCreated.getId()));
    }

    @Test
    public void findAll() {
        TestSubscriber<AlertNotifier> testObserver1 = alertNotifierRepository.findAll(ReferenceType.DOMAIN, DOMAIN_ID).test();

        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertNoValues();
        AlertNotifier alertNotifierToCreate1 = buildAlertNotifier();
        AlertNotifier alertNotifierToCreate2 = buildAlertNotifier();
        alertNotifierToCreate2.setReferenceId("domain#2");
        AlertNotifier alertNotifierCreated1 = alertNotifierRepository.create(alertNotifierToCreate1).blockingGet();
        alertNotifierRepository.create(alertNotifierToCreate2).blockingGet();

        testObserver1 = alertNotifierRepository.findAll(ReferenceType.DOMAIN, DOMAIN_ID).test();

        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertValue(alertNotifier -> alertNotifier.getId().equals(alertNotifierCreated1.getId()));
    }

    @Test
    public void findByCriteriaWithEmptyNotifierIdList() {
        TestSubscriber<AlertNotifier> testObserver1 = alertNotifierRepository.findAll(ReferenceType.DOMAIN, DOMAIN_ID).test();

        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertNoValues();
        AlertNotifier alertNotifierToCreate1 = buildAlertNotifier();
        AlertNotifier alertNotifierToCreate2 = buildAlertNotifier();
        alertNotifierToCreate2.setReferenceId("domain#2");
        AlertNotifier alertNotifierCreated1 = alertNotifierRepository.create(alertNotifierToCreate1).blockingGet();
        alertNotifierRepository.create(alertNotifierToCreate2).blockingGet();

        final AlertNotifierCriteria criteria = new AlertNotifierCriteria();
        criteria.setIds(Collections.emptyList());
        testObserver1 = alertNotifierRepository.findByCriteria(ReferenceType.DOMAIN, DOMAIN_ID, criteria).test();

        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertValue(alertNotifier -> alertNotifier.getId().equals(alertNotifierCreated1.getId()));
    }

    private AlertNotifier buildAlertNotifier() {
        AlertNotifier alertNotifier = new AlertNotifier();
        alertNotifier.setId(RandomString.generate());
        alertNotifier.setEnabled(true);
        alertNotifier.setName("alert-notifier-name");
        alertNotifier.setReferenceType(ReferenceType.DOMAIN);
        alertNotifier.setReferenceId(DOMAIN_ID);
        alertNotifier.setConfiguration("{}");
        alertNotifier.setType("webhook");
        alertNotifier.setCreatedAt(new Date());
        alertNotifier.setUpdatedAt(new Date());
        return alertNotifier;
    }
}
