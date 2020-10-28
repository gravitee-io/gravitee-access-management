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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.model.Reporter;
import io.gravitee.am.repository.jdbc.management.AbstractManagementJdbcTest;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JdbcRepositoryRepositoryTest extends AbstractManagementJdbcTest {

    @Autowired
    private ReporterRepository repository;

    @Test
    public void shouldCreate() {
        Reporter reporter = buildReporter();

        TestObserver<Reporter> testObserver = repository.create(reporter).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.getId() != null);
        assertEqualsTo(reporter, testObserver);
    }

    @Test
    public void shouldFindById() {
        Reporter reporter = buildReporter();
        Reporter createdReporter = repository.create(reporter).blockingGet();

        TestObserver<Reporter> testObserver = repository.findById(createdReporter.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.getId().equals(createdReporter.getId()));
        assertEqualsTo(reporter, testObserver);
    }

    @Test
    public void shouldUpdate() {
        Reporter reporter = buildReporter();
        Reporter createdReporter = repository.create(reporter).blockingGet();

        TestObserver<Reporter> testObserver = repository.findById(createdReporter.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.getId().equals(createdReporter.getId()));
        assertEqualsTo(reporter, testObserver);

        Reporter updatableReporter = buildReporter();
        updatableReporter.setId(createdReporter.getId());
        Reporter updatedReporter = repository.update(updatableReporter).blockingGet();

        testObserver = repository.findById(createdReporter.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.getId().equals(createdReporter.getId()));
        assertEqualsTo(updatableReporter, testObserver);
    }

    @Test
    public void shouldDelete() {
        Reporter reporter = buildReporter();
        Reporter createdReporter = repository.create(reporter).blockingGet();

        TestObserver<Reporter> testObserver = repository.findById(createdReporter.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.getId().equals(createdReporter.getId()));

        TestObserver<Void> deleteObserver = repository.delete(createdReporter.getId()).test();
        deleteObserver.awaitTerminalEvent();
        deleteObserver.assertNoErrors();

        testObserver = repository.findById(createdReporter.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindAll() {
        final int loop = 10;
        for (int i =0; i < loop; ++i) {
            Reporter reporter = buildReporter();
            repository.create(reporter).blockingGet();
        }

        TestObserver<List<Reporter>> testObserver = repository.findAll().test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.size() == loop);
        testObserver.assertValue( p -> p.stream().map(Reporter::getId).distinct().count() == loop);
    }

    @Test
    public void shouldFindByDomain() {
        final int loop = 10;
        final String domain = "fixedDomainId";
        for (int i =0; i < loop; ++i) {
            Reporter reporter = buildReporter();
            if (i % 2 == 0) reporter.setDomain(domain);
            repository.create(reporter).blockingGet();
        }

        TestObserver<List<Reporter>> testObserver = repository.findByDomain(domain).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue( p -> p.size() == loop/2);
        testObserver.assertValue( p -> p.stream().map(Reporter::getId).distinct().count() == loop/2);
    }

    private void assertEqualsTo(Reporter reporter, TestObserver<Reporter> testObserver) {
        testObserver.assertValue(p -> p.getName().equals(reporter.getName()));
        testObserver.assertValue(p -> p.getType().equals(reporter.getType()));
        testObserver.assertValue(p -> p.getDomain().equals(reporter.getDomain()));
        testObserver.assertValue(p -> p.getDataType().equals(reporter.getDataType()));
        testObserver.assertValue(p -> p.getConfiguration().equals(reporter.getConfiguration()));
        testObserver.assertValue(p -> p.isEnabled() == reporter.isEnabled());
    }

    private Reporter buildReporter() {
        Reporter reporter = new Reporter();
        String random = UUID.randomUUID().toString();
        reporter.setConfiguration("config"+random);
        reporter.setDomain("domain"+random);
        reporter.setEnabled(true);
        reporter.setName("name"+random);
        reporter.setType("type"+random);
        reporter.setDataType("data"+random);
        reporter.setCreatedAt(new Date());
        reporter.setUpdatedAt(new Date());
        return reporter;
    }
}
