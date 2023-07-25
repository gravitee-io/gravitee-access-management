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

import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria.Builder;
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import io.gravitee.am.reporter.jdbc.tool.DatabaseUrlProvider;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {DatabaseUrlProvider.class, JdbcReporterJUnitConfiguration.class}, loader = AnnotationConfigContextLoader.class)
public class JdbcAuditReporterTest {

    public static final String MY_USER = "MyUser";
    @Autowired
    protected DatabaseUrlProvider provider;

    protected AuditReporter auditReporter;

    @Autowired
    protected ApplicationContext context;

    @Before
    public void init() throws Exception {
        auditReporter = new JdbcAuditReporter();
        context.getAutowireCapableBeanFactory().autowireBean(auditReporter);
        if (auditReporter instanceof InitializingBean) {
            ((InitializingBean) auditReporter).afterPropertiesSet();
        }
        // wait for the schema initialization
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testReporter_aggregationHistogram() {
        int loop = 10;
        Random random = new Random();
        Instant now = Instant.now();
        for (int i = 0; i < loop; ++i) {
            Audit reportable = buildRandomAudit(ReferenceType.DOMAIN, "testReporter_aggregationHistogram");
            reportable.setType("FIXED_TYPE");
            reportable.setTimestamp(now.plusMillis((i * 60000)));
            // first insert doesn't match the criteria
            auditReporter.report(reportable);

            // second insert match criteria with success status
            reportable = buildRandomAudit(ReferenceType.DOMAIN, "testReporter_aggregationHistogram");
            reportable.getTarget().setAlternativeId(MY_USER);
            reportable.setType("FIXED_TYPE");
            reportable.setTimestamp(now.plusMillis((i * 60000)));
            auditReporter.report(reportable);

            // third insert match criteria with failure status
            reportable = buildRandomAudit(ReferenceType.DOMAIN, "testReporter_aggregationHistogram");
            reportable.getTarget().setAlternativeId(MY_USER);
            reportable.getOutcome().setStatus("FAILURE");
            reportable.setType("FIXED_TYPE");
            reportable.setTimestamp(now.plusMillis((i * 60000)));
            auditReporter.report(reportable);
        }

        waitBulkLoadFlush();

        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder()
                .user(MY_USER)
                .from(now.toEpochMilli())
                .to(now.plusMillis((loop * 60000)).toEpochMilli())
                .interval(60000)
                .types(Arrays.asList("FIXED_TYPE"))
                .build();
        TestObserver<Map<Object, Object>> test = auditReporter.aggregate(ReferenceType.DOMAIN, "testReporter_aggregationHistogram", criteria, Type.DATE_HISTO).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertNoErrors();

        test.assertValue(map -> map.size() == 2);
        final String failureKey = "FIXED_TYPE_FAILURE".toLowerCase();
        final String successKey = "FIXED_TYPE_SUCCESS".toLowerCase();
        test.assertValue(map -> map.get(failureKey) != null && ((List<Long>) map.get(failureKey)).stream().distinct().filter(i -> i != 0).count() == 1);
        test.assertValue(map -> map.get(failureKey) != null && ((List<Long>) map.get(failureKey)).stream().distinct().filter(i -> i != 0).findFirst().get() == 1);
        test.assertValue(map -> map.get(successKey) != null && ((List<Long>) map.get(successKey)).stream().distinct().filter(i -> i != 0).count() == 1);
        test.assertValue(map -> map.get(successKey) != null && ((List<Long>) map.get(successKey)).stream().distinct().filter(i -> i != 0).findFirst().get() == 1);
    }

    @Test
    public void testReporter_aggregationGroupBy() {
        int loop = 10;
        int accFailure = 0;
        int accSuccess = 0;
        Random random = new Random();
        for (int i = 0; i < loop; ++i) {
            Audit reportable = buildRandomAudit(ReferenceType.DOMAIN, "testReporter_aggregationGroupBy");
            if (i % 2 == 0) {
                if (random.nextBoolean()) {
                    reportable.getActor().setAlternativeId(MY_USER);
                    accSuccess++;
                } else {
                    reportable.getTarget().setAlternativeId(MY_USER);
                    reportable.getOutcome().setStatus("FAILURE");
                    accFailure++;
                }
            }
            auditReporter.report(reportable);
        }

        waitBulkLoadFlush();

        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder()
                .user(MY_USER)
                .field("outcome.status")
                .build();
        TestObserver<Map<Object, Object>> test = auditReporter.aggregate(ReferenceType.DOMAIN, "testReporter_aggregationGroupBy", criteria, Type.GROUP_BY).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertNoErrors();

        int expectedFailure = accFailure;
        int expectedSuccess = accSuccess;
        test.assertValue(map -> map.size() == ((expectedFailure > 0 && expectedSuccess > 0) ? 2 : 1));
        test.assertValue(map -> expectedFailure == 0 || map.containsKey("FAILURE"));
        test.assertValue(map -> expectedSuccess == 0 || map.containsKey("SUCCESS"));
        test.assertValue(map -> expectedFailure == 0 || ((Number) map.get("FAILURE")).intValue() == expectedFailure);
        test.assertValue(map -> expectedSuccess == 0 || ((Number) map.get("SUCCESS")).intValue() == expectedSuccess);
    }

    @Test
    public void testReporter_aggregationCount() {
        int loop = 10;
        int acc = 0;

        Random random = new Random();
        for (int i = 0; i < loop; ++i) {
            Audit reportable = buildRandomAudit(ReferenceType.DOMAIN, "testReporter_aggregationCount");
            if (i % 2 == 0) {
                if (random.nextBoolean()) {
                    reportable.getActor().setAlternativeId(MY_USER);
                } else {
                    reportable.getTarget().setAlternativeId(MY_USER);
                }
                acc++;
            }
            auditReporter.report(reportable);
        }

        waitBulkLoadFlush();

        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder().user(MY_USER).build();
        TestObserver<Map<Object, Object>> test = auditReporter.aggregate(ReferenceType.DOMAIN, "testReporter_aggregationCount", criteria, Type.COUNT).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertNoErrors();
        test.assertValue(map -> map.size() == 1);
        test.assertValue(map -> map.containsKey("data"));
        int expectedResult = acc;
        test.assertValue(map -> map.get("data") != null && ((Long) map.get("data")).intValue() == expectedResult);
    }

    @Test
    public void testReporter_aggregationCount_accessPointId() {
        int loop = 10;
        int acc = 0;

        String accessPointId = UUID.randomUUID().toString();

        for (int i = 0; i < loop; ++i) {
            Audit reportable = buildRandomAudit(ReferenceType.DOMAIN, "testReporter_aggregationCount");
            if (i % 2 == 0) {
                reportable.getAccessPoint().setId(accessPointId);
                acc++;
            }
            auditReporter.report(reportable);
        }

        waitBulkLoadFlush();

        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder().accessPointId(accessPointId).build();
        TestObserver<Map<Object, Object>> test = auditReporter.aggregate(ReferenceType.DOMAIN, "testReporter_aggregationCount", criteria, Type.COUNT).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertNoErrors();
        test.assertValue(map -> map.size() == 1);
        test.assertValue(map -> map.containsKey("data"));
        int expectedResult = acc;
        test.assertValue(map -> map.get("data") != null && ((Long) map.get("data")).intValue() == expectedResult);
    }

    @Test
    public void testReporter_searchUser() {
        int loop = 10;
        int acc = 0;
        Random random = new Random();
        for (int i = 0; i < loop; ++i) {
            Audit reportable = buildRandomAudit(ReferenceType.DOMAIN, "testReporter_searchUser");
            if (i % 2 == 0) {
                if (random.nextBoolean()) {
                    reportable.getActor().setAlternativeId(MY_USER);
                } else {
                    reportable.getTarget().setAlternativeId(MY_USER);
                }
                acc++;
            }
            auditReporter.report(reportable);
        }

        waitBulkLoadFlush();

        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder().user(MY_USER).build();
        TestObserver<Page<Audit>> test = auditReporter.search(ReferenceType.DOMAIN, "testReporter_searchUser", criteria, 0, 20).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertNoErrors();
        int expectedResult = acc;
        test.assertValue(page -> page.getTotalCount() == expectedResult);
        test.assertValue(page -> page.getCurrentPage() == 0);
        test.assertValue(page -> page.getData() != null && page.getData().size() == expectedResult);
        test.assertValue(page -> page.getData().stream().map(Audit::getId).distinct().count() == expectedResult);
    }

    @Test
    public void testReporter_searchTypes() {
        int loop = 10;
        List<String> types = new ArrayList<>();
        for (int i = 0; i < loop; ++i) {
            Audit reportable = buildRandomAudit(ReferenceType.DOMAIN, "testReporter_searchTypes");
            if (i % 2 == 0) {
                types.add(reportable.getType());
            }
            auditReporter.report(reportable);
        }

        waitBulkLoadFlush();

        AuditReportableCriteria criteria = new AuditReportableCriteria.Builder().types(types).build();
        TestObserver<Page<Audit>> test = auditReporter.search(ReferenceType.DOMAIN, "testReporter_searchTypes", criteria, 0, 20).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertNoErrors();
        test.assertValue(page -> page.getTotalCount() == types.size());
        test.assertValue(page -> page.getCurrentPage() == 0);
        test.assertValue(page -> page.getData() != null && page.getData().size() == types.size());
        test.assertValue(page -> page.getData().stream().map(Audit::getId).distinct().count() == types.size());
        test.assertValue(page -> page.getData().stream().map(Audit::getType).toList().containsAll(types));
    }

    @Test
    public void test_reporter_search() {
        int loop = 10;
        for (int i = 0; i < loop; ++i) {
            Audit reportable = buildRandomAudit(ReferenceType.DOMAIN, "testReporter_search");
            auditReporter.report(reportable);
        }

        waitBulkLoadFlush();

        final Single<Page<Audit>> testReporterSearch = auditReporter.search(ReferenceType.DOMAIN, "testReporter_search", new Builder().build(), 0, 20);
        TestObserver<Page<Audit>> test = testReporterSearch.test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertNoErrors();
        test.assertValue(page -> page.getTotalCount() == loop);
        test.assertValue(page -> page.getCurrentPage() == 0);
        test.assertValue(page -> page.getData() != null && page.getData().size() == loop);
        test.assertValue(page -> page.getData().stream().map(Audit::getId).distinct().count() == loop);
    }

    @Test
    public void testReporter_findById() {
        List<Audit> reportables = new ArrayList<>();
        int loop = 10;
        for (int i = 0; i < loop; ++i) {
            Audit reportable = buildRandomAudit(ReferenceType.DOMAIN, "testReporter_findById");
            reportables.add(reportable);
            auditReporter.report(reportable);
        }

        waitBulkLoadFlush();

        Audit audit = reportables.get(new Random().nextInt(loop));

        TestObserver<Audit> test = auditReporter.findById(audit.getReferenceType(), audit.getReferenceId(), audit.getId()).test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertNoErrors();
        assertReportEqualsTo(audit, test);
    }

    protected void assertReportEqualsTo(Audit audit, TestObserver<Audit> test) {
        test.assertValue(a -> a.getType().equals(audit.getType()));
        test.assertValue(a -> a.getTarget() != null);
        test.assertValue(a -> a.getTarget().getAlternativeId().equals(audit.getTarget().getAlternativeId()));

        test.assertValue(a -> a.getActor() != null);
        test.assertValue(a -> a.getActor().getAlternativeId().equals(audit.getActor().getAlternativeId()));

        test.assertValue(a -> a.getOutcome() != null);
        test.assertValue(a -> a.getOutcome().getStatus().equals(audit.getOutcome().getStatus()));

        test.assertValue(a -> a.getAccessPoint() != null);
        test.assertValue(a -> a.getAccessPoint().getId().equals(audit.getAccessPoint().getId()));
    }

    protected Audit buildRandomAudit(ReferenceType refType, String refId) {
        String random = UUID.randomUUID().toString();

        Audit reportable = new Audit();
        reportable.setId(random);
        reportable.setType("type" + random);
        reportable.setTransactionId("transaction" + random);
        reportable.setReferenceType(refType);
        reportable.setReferenceId(refId);
        reportable.setTimestamp(Instant.now());

        AuditEntity target = new AuditEntity();
        target.setReferenceType(ReferenceType.ORGANIZATION);
        target.setReferenceId("org" + random);
        target.setAlternativeId("altid" + random);
        target.setType("type" + random);
        target.setAttributes(Collections.singletonMap("key1", "value1"));
        reportable.setTarget(target);

        AuditEntity actor = new AuditEntity();
        actor.setReferenceType(ReferenceType.ENVIRONMENT);
        actor.setReferenceId("env" + random);
        actor.setAlternativeId("altid" + random);
        actor.setType("type" + random);
        actor.setAttributes(Collections.singletonMap("key1", "value1"));
        reportable.setActor(actor);

        AuditOutcome outcome = new AuditOutcome();
        outcome.setStatus("SUCCESS");
        outcome.setMessage("Message" + random);
        reportable.setOutcome(outcome);

        AuditAccessPoint accessPoint = new AuditAccessPoint();
        accessPoint.setId("id" + random);
        accessPoint.setIpAddress("127.0.0.1");
        accessPoint.setUserAgent("useragent" + random);
        reportable.setAccessPoint(accessPoint);
        return reportable;
    }

    protected void waitBulkLoadFlush() {
        try {
            Thread.sleep(3000); // bulk load set to
        } catch (InterruptedException e) {
        }
    }
}
