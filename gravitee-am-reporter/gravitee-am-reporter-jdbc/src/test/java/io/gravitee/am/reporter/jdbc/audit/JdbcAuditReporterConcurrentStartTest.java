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
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import io.gravitee.am.reporter.jdbc.JdbcReporterConfiguration;
import io.gravitee.am.reporter.jdbc.tool.DatabaseUrlProvider;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The management API and the gateway each start a reporter for a new domain, so two reporters create
 * the same audit tables at the same time.
 *
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
        for (int round = 0; round < ROUNDS; round++) {
            String tableSuffix = "race" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String domain = "domain-" + tableSuffix;
            JdbcAuditReporter first = reporter(tableSuffix);
            JdbcAuditReporter second = reporter(tableSuffix);

            first.afterPropertiesSet();
            second.afterPropertiesSet();

            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                first.report(audit(domain));
                second.report(audit(domain));
                assertThat(countAudits(first, domain)).isPositive();
                assertThat(countAudits(second, domain)).isPositive();
            });
        }
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

    private static long countAudits(JdbcAuditReporter reporter, String domain) {
        return reporter.search(ReferenceType.DOMAIN, domain, new AuditReportableCriteria.Builder().build(), 0, 50)
                .blockingGet()
                .getData()
                .size();
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
