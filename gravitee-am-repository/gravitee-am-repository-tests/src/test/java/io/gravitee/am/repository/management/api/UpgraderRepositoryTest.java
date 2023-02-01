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
import io.gravitee.node.api.upgrader.UpgradeRecord;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

/**
 * @author Kamiel Ahmadpour (kamiel.ahmadpour at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UpgraderRepositoryTest extends AbstractManagementTest {

    @Autowired
    private UpgraderRepository upgraderRepository;

    private UpgradeRecord buildUpgrader(String id) {
        UpgradeRecord record = new UpgradeRecord();
        record.setId(id);
        record.setAppliedAt(new Date());

        return record;
    }

    @Test
    public void testFindById() throws TechnicalException {
        UpgradeRecord record = buildUpgrader(this.getClass().getName());
        UpgradeRecord themeCreated = upgraderRepository.create(record).blockingGet();

        TestObserver<UpgradeRecord> testObserver = upgraderRepository.findById(themeCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getId().equals(this.getClass().getName()));
        testObserver.assertValue(d -> d.getAppliedAt() != null);
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        upgraderRepository.findById("unknown").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        UpgradeRecord record = buildUpgrader(this.getClass().getName());
        TestObserver<UpgradeRecord> testObserver = upgraderRepository.create(record).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getId().equals(this.getClass().getName()));
        testObserver.assertValue(d -> d.getAppliedAt() != null);
    }

}
