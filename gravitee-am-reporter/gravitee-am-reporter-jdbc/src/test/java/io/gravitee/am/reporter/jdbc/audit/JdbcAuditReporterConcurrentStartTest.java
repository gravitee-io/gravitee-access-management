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
package io.gravitee.am.reporter.jdbc.audit;

import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import io.gravitee.am.reporter.jdbc.JdbcReporterConfiguration;
import io.gravitee.am.reporter.jdbc.tool.DatabaseUrlProvider;
import io.reactivex.rxjava3.disposables.Disposable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * @author GraviteeSource Team
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {DatabaseUrlProvider.class, JdbcReporterJUnitConfiguration.class}, loader = AnnotationConfigContextLoader.class)
public class JdbcAuditReporterConcurrentStartTest {

    private static final int ROUNDS = 5;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcReporterConfiguration configuration;

    @Test
    public void bothReportersShouldStoreAuditsWhenTheyCreateTheSameTablesTogether() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String tableSuffix = "race" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                String domain = "domain-" + tableSuffix;
                JdbcAuditReporter first = reporter(tableSuffix);
                JdbcAuditReporter second = reporter(tableSuffix);
                try {
                    CountDownLatch start = new CountDownLatch(1);
                    Future<?> firstStart = executor.submit(() -> startOn(start, first));
                    Future<?> secondStart = executor.submit(() -> startOn(start, second));
                    start.countDown();
                    firstStart.get();
                    secondStart.get();

                    awaitStored(first, domain);
                    awaitStored(second, domain);
                } finally {
                    stopBulkProcessor(first);
                    stopBulkProcessor(second);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Leaves the connection pool open, since every reporter in this context shares it.
     */
    private static void stopBulkProcessor(JdbcAuditReporter reporter) {
        Disposable disposable = (Disposable) ReflectionTestUtils.getField(reporter, "disposable");
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private static Void startOn(CountDownLatch start, JdbcAuditReporter reporter) throws Exception {
        start.await();
        reporter.afterPropertiesSet();
        return null;
    }

    /**
     * Both reporters write to the same tables, so only an id this reporter reported proves it stores audits.
     */
    private static void awaitStored(JdbcAuditReporter reporter, String domain) {
        List<String> reported = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Audit audit = audit(domain);
            reported.add(audit.getId());
            reporter.report(audit);
            assertThat(reported).anyMatch(id -> reporter.findById(ReferenceType.DOMAIN, domain, id).blockingGet() != null);
        });
    }

    private JdbcAuditReporter reporter(String tableSuffix) {
        JdbcAuditReporter reporter = new JdbcAuditReporter();
        context.getAutowireCapableBeanFactory().autowireBean(reporter);
        JdbcReporterConfiguration ownConfiguration = new JdbcReporterConfiguration();
        BeanUtils.copyProperties(configuration, ownConfiguration);
        ownConfiguration.setTableSuffix(tableSuffix);
        ReflectionTestUtils.setField(reporter, "configuration", ownConfiguration);
        return reporter;
    }

    private static Audit audit(String domain) {
        Audit audit = new Audit();
        audit.setId(UUID.randomUUID().toString());
        audit.setType("TOKEN_CREATED");
        audit.setReferenceType(ReferenceType.DOMAIN);
        audit.setReferenceId(domain);
        audit.setTimestamp(Instant.now());
        AuditOutcome outcome = new AuditOutcome();
        outcome.setStatus(Status.SUCCESS);
        audit.setOutcome(outcome);
        return audit;
    }
}
