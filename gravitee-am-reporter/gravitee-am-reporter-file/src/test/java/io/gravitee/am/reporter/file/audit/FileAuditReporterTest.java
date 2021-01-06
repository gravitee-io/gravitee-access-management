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
package io.gravitee.am.reporter.file.audit;

import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import io.gravitee.am.reporter.file.formatter.Type;
import io.gravitee.common.service.AbstractService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

import static org.junit.Assert.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */

public abstract class FileAuditReporterTest {

    protected AuditReporter auditReporter;

    @Autowired
    protected ApplicationContext context;

    @Before
    public void init() throws Exception {
        auditReporter = new FileAuditReporter();

        context.getAutowireCapableBeanFactory().autowireBean(auditReporter);
        ((AbstractService)auditReporter).setApplicationContext(context);

        if (auditReporter instanceof InitializingBean) {
            ((InitializingBean) auditReporter).afterPropertiesSet();
        }

        auditReporter.start();
        waitBulkLoadFlush();
    }

    @After
    public void shutdown() throws Exception {
        if (auditReporter != null) {
            auditReporter.stop();
        }
        Files.delete(Paths.get(buildAuditLogsFilename()));
    }

    @Test
    public void testReporter() throws Exception {
        List<Audit> reportables = new ArrayList<>();
        int loop = 10;
        for (int i = 0; i < loop; ++i){
            Audit reportable = buildRandomAudit(ReferenceType.DOMAIN, "testReporter");
            reportables.add(reportable);
            auditReporter.report(reportable);
        }

        waitBulkLoadFlush();

        checkAuditLogs(reportables, loop);
    }

    protected abstract void checkAuditLogs(List<Audit> reportables, int loop) throws IOException;

    protected void assertReportEqualsTo(Audit audit, AuditEntry test) {
        assertTrue( test.getType().equals(audit.getType()));
        assertTrue( test.getTarget() != null);
        assertTrue( test.getTarget().getAlternativeId().equals(audit.getTarget().getAlternativeId()));

        assertTrue( test.getActor() != null);
        assertTrue( test.getActor().getAlternativeId().equals(audit.getActor().getAlternativeId()));

        assertTrue( test.getStatus() != null);
        assertTrue( test.getStatus().equals(audit.getOutcome().getStatus()));

        assertTrue( test.getAccessPoint() != null);
        assertTrue( test.getAccessPoint().getId().equals(audit.getAccessPoint().getId()));
    }

    protected Audit buildRandomAudit(ReferenceType refType, String refId) {
        String random = UUID.randomUUID().toString();

        Audit reportable = new Audit();
        reportable.setId(random);
        reportable.setType("type"+random);
        reportable.setTransactionId("transaction"+random);
        reportable.setReferenceType(refType);
        reportable.setReferenceId(refId);
        reportable.setTimestamp(Instant.now());

        AuditEntity target = new AuditEntity();
        target.setReferenceType(ReferenceType.ORGANIZATION);
        target.setReferenceId("org"+random);
        target.setAlternativeId("altid"+random);
        target.setType("type"+random);
        target.setAttributes(Collections.singletonMap("key1", "value1"));
        reportable.setTarget(target);

        AuditEntity actor = new AuditEntity();
        actor.setReferenceType(ReferenceType.ENVIRONMENT);
        actor.setReferenceId("env"+random);
        actor.setAlternativeId("altid"+random);
        actor.setType("type"+random);
        actor.setAttributes(Collections.singletonMap("key1", "value1"));
        reportable.setActor(actor);

        AuditOutcome outcome = new AuditOutcome();
        outcome.setStatus("SUCCESS");
        outcome.setMessage("Message"+random);
        reportable.setOutcome(outcome);

        AuditAccessPoint accessPoint = new AuditAccessPoint();
        accessPoint.setId("id"+random);
        accessPoint.setIpAddress("127.0.0.1");
        accessPoint.setUserAgent("useragent"+random);
        reportable.setAccessPoint(accessPoint);
        return reportable;
    }

    protected void waitBulkLoadFlush() {
        try {
            Thread.sleep(1000); // bulk load set to
        } catch (InterruptedException e) {
        }
    }

    protected String buildAuditLogsFilename() {
        GraviteeContext context = GraviteeContext.defaultContext("domain");
        String filename = Paths.get(System.getProperty(FileAuditReporter.REPORTERS_FILE_DIRECTORY),
                context.getOrganizationId(),
                context.getEnvironmentId(),
                context.getDomainId(),
                "FileAuditReporterTest-" + new SimpleDateFormat("yyyy_MM_dd").format(new Date(Instant.now().toEpochMilli())) + "." +  Type.valueOf(System.getProperty(FileAuditReporter.REPORTERS_FILE_OUTPUT).toUpperCase()).getExtension())
                .toFile().getPath();
        return filename;
    }
}
