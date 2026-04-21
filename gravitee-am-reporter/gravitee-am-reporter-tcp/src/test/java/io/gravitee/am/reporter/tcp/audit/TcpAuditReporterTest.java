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
package io.gravitee.am.reporter.tcp.audit;

import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.common.utils.WriteStreamRegistry;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import io.gravitee.am.reporter.tcp.TcpReporterConfiguration;
import io.gravitee.am.reporter.tcp.client.TcpWriteStream;
import io.gravitee.node.api.Node;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TcpAuditReporter}.
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TcpAuditReporterTest {

    @TempDir
    Path tempDir;

    @Mock
    private Node node;

    @Mock
    private TcpWriteStream tcpWriteStream;

    private WriteStreamRegistry writeStreamRegistry;
    private GraviteeContext context;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        when(node.id()).thenReturn("node-id");
        when(node.hostname()).thenReturn("node-hostname");

        context = GraviteeContext.defaultContext("my-domain");

        environment = new MockEnvironment();
        environment.setProperty(TcpAuditReporter.PROP_FALLBACK_DIR, tempDir.toString());
        environment.setProperty(TcpAuditReporter.PROP_FALLBACK_DIR_ENABLED, "true");

        writeStreamRegistry = new WriteStreamRegistry() {
            @Override
            public io.vertx.core.streams.WriteStream getOrCreate(String streamId,
                                                                  java.util.function.Supplier<io.vertx.core.streams.WriteStream> supplier) {
                return tcpWriteStream;
            }

            @Override
            public Optional<io.vertx.core.streams.WriteStream> decreaseUsage(String streamId) {
                return Optional.empty();
            }
        };
    }

    private TcpReporterConfiguration defaultConfig() {
        TcpReporterConfiguration cfg = new TcpReporterConfiguration();
        cfg.setHost("localhost");
        cfg.setPort(9000);
        cfg.setOutput("JSON");
        return cfg;
    }

    private TcpAuditReporter buildReporter() throws Exception {
        return buildReporter(defaultConfig());
    }

    private TcpAuditReporter buildReporter(TcpReporterConfiguration cfg) throws Exception {
        Vertx realVertx = Vertx.vertx();
        TcpAuditReporter reporter = new TcpAuditReporter();
        setPrivateField(reporter, "config", cfg);
        setPrivateField(reporter, "environment", environment);
        setPrivateField(reporter, "vertx", realVertx);
        setPrivateField(reporter, "context", context);
        setPrivateField(reporter, "writeStreamRegistry", writeStreamRegistry);
        setPrivateField(reporter, "node", node);

        StaticApplicationContext appCtx = new StaticApplicationContext();
        appCtx.refresh();
        reporter.setApplicationContext(appCtx);

        reporter.afterPropertiesSet();
        reporter.start();
        return reporter;
    }

    // -------------------------------------------------------------------------
    // canHandle
    // -------------------------------------------------------------------------

    @Test
    void canHandle_shouldReturnTrue_forAudit() throws Exception {
        TcpAuditReporter reporter = buildReporter();
        assertThat(reporter.canHandle(buildAudit())).isTrue();
    }

    // -------------------------------------------------------------------------
    // report() — happy path (TCP connected)
    // -------------------------------------------------------------------------

    @Test
    void report_shouldWriteToTcp_whenConnected() throws Exception {
        when(tcpWriteStream.isConnected()).thenReturn(true);
        when(tcpWriteStream.write(any(Buffer.class))).thenReturn(Future.succeededFuture());

        TcpAuditReporter reporter = buildReporter();
        reporter.report(buildAudit());

        verify(tcpWriteStream).write(any(Buffer.class));
    }

    @Test
    void report_shouldEnrichWithNodeInfo() throws Exception {
        when(tcpWriteStream.isConnected()).thenReturn(true);
        ArgumentCaptor<Buffer> bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        when(tcpWriteStream.write(bufferCaptor.capture())).thenReturn(Future.succeededFuture());

        TcpAuditReporter reporter = buildReporter();
        reporter.report(buildAudit());

        String json = bufferCaptor.getValue().toString();
        assertThat(json).contains("\"nodeId\":\"node-id\"");
        assertThat(json).contains("\"nodeHostname\":\"node-hostname\"");
    }

    @Test
    void report_shouldEnrichWithContextOrganizationAndEnvironment() throws Exception {
        when(tcpWriteStream.isConnected()).thenReturn(true);
        ArgumentCaptor<Buffer> bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        when(tcpWriteStream.write(bufferCaptor.capture())).thenReturn(Future.succeededFuture());

        TcpAuditReporter reporter = buildReporter();
        reporter.report(buildAudit());

        String json = bufferCaptor.getValue().toString();
        assertThat(json).contains("\"organizationId\":\"" + context.getOrganizationId() + "\"");
        assertThat(json).contains("\"environmentId\":\"" + context.getEnvironmentId() + "\"");
    }

    @Test
    void report_shouldSanitiseInvalidIpAddress() throws Exception {
        when(tcpWriteStream.isConnected()).thenReturn(true);
        ArgumentCaptor<Buffer> bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        when(tcpWriteStream.write(bufferCaptor.capture())).thenReturn(Future.succeededFuture());

        TcpAuditReporter reporter = buildReporter();
        Audit audit = buildAudit();
        audit.getAccessPoint().setIpAddress("not-an-ip");
        reporter.report(audit);

        String json = bufferCaptor.getValue().toString();
        assertThat(json).contains("\"ipAddress\":\"0.0.0.0\"");
    }

    @Test
    void report_shouldStripActorAndTargetAttributes() throws Exception {
        when(tcpWriteStream.isConnected()).thenReturn(true);
        ArgumentCaptor<Buffer> bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        when(tcpWriteStream.write(bufferCaptor.capture())).thenReturn(Future.succeededFuture());

        TcpAuditReporter reporter = buildReporter();
        Audit audit = buildAudit();
        audit.getActor().setAttributes(Collections.singletonMap("sensitiveKey", "sensitiveValue"));
        audit.getTarget().setAttributes(Collections.singletonMap("targetKey", "targetValue"));
        reporter.report(audit);

        String json = bufferCaptor.getValue().toString();
        assertThat(json).doesNotContain("sensitiveKey");
        assertThat(json).doesNotContain("targetKey");
    }

    // -------------------------------------------------------------------------
    // report() — fallback path (TCP disconnected or write failure)
    // -------------------------------------------------------------------------

    @Test
    void report_shouldWriteToFallbackFile_whenTcpNotConnected() throws Exception {
        when(tcpWriteStream.isConnected()).thenReturn(false);

        TcpAuditReporter reporter = buildReporter();
        reporter.report(buildAudit());

        // Give async write time to complete
        Thread.sleep(500);

        assertFallbackFileContainsEntries(1);
    }

    @Test
    void report_shouldWriteToFallbackFile_whenTcpWriteFails() throws Exception {
        when(tcpWriteStream.isConnected()).thenReturn(true);
        when(tcpWriteStream.write(any(Buffer.class))).thenReturn(Future.failedFuture("socket error"));

        TcpAuditReporter reporter = buildReporter();
        reporter.report(buildAudit());

        Thread.sleep(500);
        assertFallbackFileContainsEntries(1);
    }

    @Test
    void report_multipleFailures_shouldAppendAllToFallbackFile() throws Exception {
        when(tcpWriteStream.isConnected()).thenReturn(false);

        TcpAuditReporter reporter = buildReporter();
        for (int i = 0; i < 3; i++) {
            reporter.report(buildAudit());
        }

        Thread.sleep(600);
        assertFallbackFileContainsEntries(3);
    }

    // -------------------------------------------------------------------------
    // Fallback drain on reconnect
    // -------------------------------------------------------------------------

    @Test
    void drainFallback_shouldSendAllEntriesAndDeleteFile() throws Exception {
        // Prepare fallback file with 3 entries
        Path fallbackPath = prepareFallbackFileWithEntries(3);

        // Reporter that sends successfully
        when(tcpWriteStream.isConnected()).thenReturn(true);
        when(tcpWriteStream.write(any(Buffer.class))).thenReturn(Future.succeededFuture());

        TcpAuditReporter reporter = buildReporter();
        // Inject the fallback state and file path
        setPrivateField(reporter, "hasFallback", new AtomicBoolean(true));
        setPrivateField(reporter, "fallbackFile", fallbackPath.toString());

        // A successful write triggers fallback drain
        reporter.report(buildAudit());

        // Wait for drain to complete
        Thread.sleep(800);

        // 3 fallback entries + 1 nominal entry = at least 4 writes
        verify(tcpWriteStream, atLeast(4)).write(any(Buffer.class));

        // Fallback file should be deleted after successful drain
        assertThat(fallbackPath).doesNotExist();
    }

    @Test
    void drainFallback_shouldKeepRemainingEntries_whenDrainInterrupted() throws Exception {
        Path fallbackPath = prepareFallbackFileWithEntries(5);

        // Fail on the 3rd write (index 2 in the fallback)
        AtomicBoolean[] failOnNext = {new AtomicBoolean(false)};
        when(tcpWriteStream.isConnected()).thenReturn(true);
        when(tcpWriteStream.write(any(Buffer.class)))
                .thenAnswer(inv -> {
                    if (failOnNext[0].getAndSet(true)) {
                        return Future.failedFuture("interrupted");
                    }
                    return Future.succeededFuture();
                });

        TcpAuditReporter reporter = buildReporter();
        setPrivateField(reporter, "hasFallback", new AtomicBoolean(true));
        setPrivateField(reporter, "fallbackFile", fallbackPath.toString());

        reporter.report(buildAudit());
        Thread.sleep(600);

        // File should still exist with remaining entries
        assertThat(fallbackPath).exists();
    }

    // -------------------------------------------------------------------------
    // canSearch / search / aggregate / findById
    // -------------------------------------------------------------------------

    @Test
    void canSearch_shouldReturnFalse() throws Exception {
        TcpAuditReporter reporter = buildReporter();
        assertThat(reporter.canSearch()).isFalse();
    }

    @Test
    void search_shouldThrowIllegalState() throws Exception {
        TcpAuditReporter reporter = buildReporter();
        assertThatThrownBy(() -> reporter.search(ReferenceType.DOMAIN, "id", null, 0, 10).blockingGet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TCP reporter");
    }

    @Test
    void aggregate_shouldThrowIllegalState() throws Exception {
        TcpAuditReporter reporter = buildReporter();
        assertThatThrownBy(() -> reporter.aggregate(ReferenceType.DOMAIN, "id", null, null).blockingGet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findById_shouldThrowIllegalState() throws Exception {
        TcpAuditReporter reporter = buildReporter();
        assertThatThrownBy(() -> reporter.findById(ReferenceType.DOMAIN, "id", "auditId").blockingGet())
                .isInstanceOf(IllegalStateException.class);
    }

    // -------------------------------------------------------------------------
    // buildStreamKey
    // -------------------------------------------------------------------------

    @Test
    void buildStreamKey_shouldBeStableForSameEndpoint() throws Exception {
        TcpAuditReporter reporterA = new TcpAuditReporter();
        TcpReporterConfiguration cfgA = new TcpReporterConfiguration();
        cfgA.setHost("host1");
        cfgA.setPort(9000);
        setPrivateField(reporterA, "config", cfgA);

        TcpAuditReporter reporterB = new TcpAuditReporter();
        TcpReporterConfiguration cfgB = new TcpReporterConfiguration();
        cfgB.setHost("host1");
        cfgB.setPort(9000);
        setPrivateField(reporterB, "config", cfgB);

        assertThat(reporterA.buildStreamKey()).isEqualTo(reporterB.buildStreamKey());
    }

    @Test
    void buildStreamKey_shouldDifferForDifferentHosts() throws Exception {
        TcpAuditReporter reporterA = new TcpAuditReporter();
        TcpReporterConfiguration cfgA = new TcpReporterConfiguration();
        cfgA.setHost("host1");
        cfgA.setPort(9000);
        setPrivateField(reporterA, "config", cfgA);

        TcpAuditReporter reporterB = new TcpAuditReporter();
        TcpReporterConfiguration cfgB = new TcpReporterConfiguration();
        cfgB.setHost("host2");
        cfgB.setPort(9000);
        setPrivateField(reporterB, "config", cfgB);

        assertThat(reporterA.buildStreamKey()).isNotEqualTo(reporterB.buildStreamKey());
    }

    @Test
    void buildStreamKey_shouldDifferForDifferentPorts() throws Exception {
        TcpAuditReporter reporterA = new TcpAuditReporter();
        TcpReporterConfiguration cfgA = new TcpReporterConfiguration();
        cfgA.setHost("host1");
        cfgA.setPort(9000);
        setPrivateField(reporterA, "config", cfgA);

        TcpAuditReporter reporterB = new TcpAuditReporter();
        TcpReporterConfiguration cfgB = new TcpReporterConfiguration();
        cfgB.setHost("host1");
        cfgB.setPort(9001);
        setPrivateField(reporterB, "config", cfgB);

        assertThat(reporterA.buildStreamKey()).isNotEqualTo(reporterB.buildStreamKey());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Audit buildAudit() {
        String random = UUID.randomUUID().toString();
        Audit audit = new Audit();
        audit.setId(random);
        audit.setType("USER_LOGIN");
        audit.setTransactionId("tx-" + random);
        audit.setReferenceType(ReferenceType.DOMAIN);
        audit.setReferenceId("domain-id");
        audit.setTimestamp(Instant.now());

        AuditEntity actor = new AuditEntity();
        actor.setId("actor-" + random);
        actor.setType("USER");
        actor.setDisplayName("Test User");
        actor.setReferenceType(ReferenceType.DOMAIN);
        actor.setReferenceId("domain-id");
        audit.setActor(actor);

        AuditEntity target = new AuditEntity();
        target.setId("target-" + random);
        target.setType("APPLICATION");
        target.setReferenceType(ReferenceType.DOMAIN);
        target.setReferenceId("domain-id");
        audit.setTarget(target);

        AuditOutcome outcome = new AuditOutcome();
        outcome.setStatus(Status.SUCCESS);
        outcome.setMessage("Login successful");
        audit.setOutcome(outcome);

        AuditAccessPoint accessPoint = new AuditAccessPoint();
        accessPoint.setId("ap-" + random);
        accessPoint.setIpAddress("127.0.0.1");
        accessPoint.setUserAgent("Mozilla/5.0");
        audit.setAccessPoint(accessPoint);

        return audit;
    }

    private void assertFallbackFileContainsEntries(int expectedCount) throws IOException {
        Path fallbackDir = tempDir.resolve(context.getOrganizationId())
                .resolve(context.getEnvironmentId())
                .resolve(context.getDomainId());
        assertThat(fallbackDir).exists();

        long lineCount = Files.list(fallbackDir)
                .filter(p -> p.toString().endsWith(".b64"))
                .findFirst()
                .map(p -> {
                    try {
                        return Files.lines(p).filter(l -> !l.isBlank()).count();
                    } catch (IOException e) {
                        return 0L;
                    }
                })
                .orElse(0L);

        assertThat(lineCount).isEqualTo(expectedCount);
    }

    private Path prepareFallbackFileWithEntries(int count) throws IOException {
        Path fallbackDir = tempDir.resolve(context.getOrganizationId())
                .resolve(context.getEnvironmentId())
                .resolve(context.getDomainId());
        Files.createDirectories(fallbackDir);

        Path fallbackPath = fallbackDir.resolve("tcp-fallback-test.b64");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String payload = "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"USER_LOGIN\"}";
            sb.append(Base64.getEncoder().encodeToString(payload.getBytes())).append('\n');
        }
        Files.writeString(fallbackPath, sb.toString());
        return fallbackPath;
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), name);
            }
            throw e;
        }
    }
}
