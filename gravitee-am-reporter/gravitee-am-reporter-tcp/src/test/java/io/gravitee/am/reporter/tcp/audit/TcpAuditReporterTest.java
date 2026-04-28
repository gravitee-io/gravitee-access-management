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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
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

    private static final long FALLBACK_ASSERT_TIMEOUT_MS = 10_000;
    private static final long FALLBACK_POLL_INTERVAL_MS = 25;

    @TempDir
    Path tempDir;

    @Mock
    private Node node;

    @Mock
    private TcpWriteStream tcpWriteStream;

    @Mock
    private WriteStreamRegistry writeStreamRegistry;

    private MockEnvironment environment;
    private GraviteeContext context;

    @InjectMocks
    private TcpAuditReporter reporter;

    @BeforeEach
    void setUp() {
        when(node.id()).thenReturn("node-id");
        when(node.hostname()).thenReturn("node-hostname");

        environment = new MockEnvironment();
        environment.setProperty(TcpAuditReporter.PROP_FALLBACK_DIR, tempDir.toString());
        environment.setProperty(TcpAuditReporter.PROP_FALLBACK_DIR_ENABLED, "true");

        context = GraviteeContext.defaultContext("my-domain");

        lenient().when(writeStreamRegistry.getOrCreate(anyString(), any())).thenReturn(tcpWriteStream);
        lenient().when(writeStreamRegistry.decreaseUsage(anyString())).thenReturn(Optional.empty());
    }

    private TcpReporterConfiguration defaultConfig() {
        TcpReporterConfiguration cfg = new TcpReporterConfiguration();
        cfg.setHost("localhost");
        cfg.setPort(9000);
        cfg.setOutput("JSON");
        return cfg;
    }

    private void startReporter() throws Exception {
        TcpReporterConfiguration cfg = defaultConfig();
        ReflectionTestUtils.setField(reporter, "config", cfg);
        ReflectionTestUtils.setField(reporter, "environment", environment);
        ReflectionTestUtils.setField(reporter, "vertx", Vertx.vertx());
        ReflectionTestUtils.setField(reporter, "context", context);

        StaticApplicationContext appCtx = new StaticApplicationContext();
        appCtx.refresh();
        reporter.setApplicationContext(appCtx);

        reporter.afterPropertiesSet();
        reporter.start();
    }

    // -------------------------------------------------------------------------
    // canHandle
    // -------------------------------------------------------------------------

    @Test
    void canHandle_shouldReturnTrue_forAudit() throws Exception {
        startReporter();
        assertThat(reporter.canHandle(buildAudit())).isTrue();
    }

    // -------------------------------------------------------------------------
    // report() — happy path (TCP connected)
    // -------------------------------------------------------------------------

    @Test
    void report_shouldWriteToTcp_whenConnected() throws Exception {
        when(tcpWriteStream.isConnected()).thenReturn(true);
        when(tcpWriteStream.write(any(Buffer.class))).thenReturn(Future.succeededFuture());

        startReporter();
        reporter.report(buildAudit());

        verify(tcpWriteStream).write(any(Buffer.class));
    }

    @Test
    void report_shouldEnrichWithNodeInfo() throws Exception {
        when(tcpWriteStream.isConnected()).thenReturn(true);
        ArgumentCaptor<Buffer> bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        when(tcpWriteStream.write(bufferCaptor.capture())).thenReturn(Future.succeededFuture());

        startReporter();
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

        startReporter();
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

        startReporter();
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

        startReporter();
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

        startReporter();
        reporter.report(buildAudit());

        assertFallbackFileContainsEntries(1);
    }

    @Test
    void report_shouldWriteToFallbackFile_whenTcpWriteFails() throws Exception {
        when(tcpWriteStream.isConnected()).thenReturn(true);
        when(tcpWriteStream.write(any(Buffer.class))).thenReturn(Future.failedFuture("socket error"));

        startReporter();
        reporter.report(buildAudit());

        assertFallbackFileContainsEntries(1);
    }

    @Test
    void report_multipleFailures_shouldAppendAllToFallbackFile() throws Exception {
        when(tcpWriteStream.isConnected()).thenReturn(false);

        startReporter();
        reporter.report(buildAudit());
        awaitFallbackLineCountAtLeast(1);
        reporter.report(buildAudit());
        awaitFallbackLineCountAtLeast(2);
        reporter.report(buildAudit());

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

        startReporter();
        requireHasFallbackTrue();
        ReflectionTestUtils.setField(reporter, "fallbackFile", fallbackPath.toString());

        // A successful write triggers fallback drain
        reporter.report(buildAudit());

        // 3 fallback entries + 1 nominal entry = at least 4 writes
        verify(tcpWriteStream, timeout(FALLBACK_ASSERT_TIMEOUT_MS).atLeast(4)).write(any(Buffer.class));

        awaitPathAbsent(fallbackPath);
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

        startReporter();
        requireHasFallbackTrue();
        ReflectionTestUtils.setField(reporter, "fallbackFile", fallbackPath.toString());

        reporter.report(buildAudit());

        verify(tcpWriteStream, timeout(FALLBACK_ASSERT_TIMEOUT_MS).atLeast(2)).write(any(Buffer.class));

        // File should still exist with remaining entries
        assertThat(fallbackPath).exists();
    }

    // -------------------------------------------------------------------------
    // doStart() — restore hasFallback from disk
    // -------------------------------------------------------------------------

    @Test
    void doStart_shouldSetHasFallback_whenFallbackFileExistsFromPreviousRun() throws Exception {
        Path fallbackPath = expectedFallbackFilePath();
        Files.createDirectories(fallbackPath.getParent());
        Files.writeString(fallbackPath,
                Base64.getEncoder().encodeToString("{\"id\":\"x\",\"type\":\"USER_LOGIN\"}".getBytes()) + "\n");

        when(tcpWriteStream.isConnected()).thenReturn(false);
        startReporter();

        awaitHasFallbackTrue();
    }

    @Test
    void doStart_shouldDrainFallbackFile_whenAlreadyConnectedOnStartup() throws Exception {
        Path fallbackPath = expectedFallbackFilePath();
        Files.createDirectories(fallbackPath.getParent());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            sb.append(Base64.getEncoder().encodeToString(("{\"id\":\"" + i + "\",\"type\":\"USER_LOGIN\"}").getBytes()))
              .append('\n');
        }
        Files.writeString(fallbackPath, sb.toString());

        when(tcpWriteStream.isConnected()).thenReturn(true);
        when(tcpWriteStream.write(any(Buffer.class))).thenReturn(Future.succeededFuture());

        startReporter();

        verify(tcpWriteStream, timeout(FALLBACK_ASSERT_TIMEOUT_MS).atLeast(2)).write(any(Buffer.class));
        awaitPathAbsent(fallbackPath);
    }

    // -------------------------------------------------------------------------
    // canSearch / search / aggregate / findById
    // -------------------------------------------------------------------------

    @Test
    void canSearch_shouldReturnFalse() throws Exception {
        startReporter();
        assertThat(reporter.canSearch()).isFalse();
    }

    @Test
    void search_shouldThrowIllegalState() throws Exception {
        startReporter();
        assertThatThrownBy(() -> reporter.search(ReferenceType.DOMAIN, "id", null, 0, 10).blockingGet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TCP reporter");
    }

    @Test
    void aggregate_shouldThrowIllegalState() throws Exception {
        startReporter();
        assertThatThrownBy(() -> reporter.aggregate(ReferenceType.DOMAIN, "id", null, null).blockingGet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findById_shouldThrowIllegalState() throws Exception {
        startReporter();
        assertThatThrownBy(() -> reporter.findById(ReferenceType.DOMAIN, "id", "auditId").blockingGet())
                .isInstanceOf(IllegalStateException.class);
    }

    // -------------------------------------------------------------------------
    // buildStreamKey
    // -------------------------------------------------------------------------

    @Test
    void buildStreamKey_shouldBeStableForSameEndpoint() {
        TcpReporterConfiguration cfgA = new TcpReporterConfiguration();
        cfgA.setHost("host1");
        cfgA.setPort(9000);
        TcpAuditReporter reporterA = new TcpAuditReporter();
        ReflectionTestUtils.setField(reporterA, "config", cfgA);

        TcpReporterConfiguration cfgB = new TcpReporterConfiguration();
        cfgB.setHost("host1");
        cfgB.setPort(9000);
        TcpAuditReporter reporterB = new TcpAuditReporter();
        ReflectionTestUtils.setField(reporterB, "config", cfgB);

        assertThat(reporterA.buildStreamKey()).isEqualTo(reporterB.buildStreamKey());
    }

    @Test
    void buildStreamKey_shouldDifferForDifferentHosts() {
        TcpReporterConfiguration cfgA = new TcpReporterConfiguration();
        cfgA.setHost("host1");
        cfgA.setPort(9000);
        TcpAuditReporter reporterA = new TcpAuditReporter();
        ReflectionTestUtils.setField(reporterA, "config", cfgA);

        TcpReporterConfiguration cfgB = new TcpReporterConfiguration();
        cfgB.setHost("host2");
        cfgB.setPort(9000);
        TcpAuditReporter reporterB = new TcpAuditReporter();
        ReflectionTestUtils.setField(reporterB, "config", cfgB);

        assertThat(reporterA.buildStreamKey()).isNotEqualTo(reporterB.buildStreamKey());
    }

    @Test
    void buildStreamKey_shouldDifferForDifferentPorts() {
        TcpReporterConfiguration cfgA = new TcpReporterConfiguration();
        cfgA.setHost("host1");
        cfgA.setPort(9000);
        TcpAuditReporter reporterA = new TcpAuditReporter();
        ReflectionTestUtils.setField(reporterA, "config", cfgA);

        TcpReporterConfiguration cfgB = new TcpReporterConfiguration();
        cfgB.setHost("host1");
        cfgB.setPort(9001);
        TcpAuditReporter reporterB = new TcpAuditReporter();
        ReflectionTestUtils.setField(reporterB, "config", cfgB);

        assertThat(reporterA.buildStreamKey()).isNotEqualTo(reporterB.buildStreamKey());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void requireHasFallbackTrue() {
        AtomicBoolean hasFallback = (AtomicBoolean) ReflectionTestUtils.getField(reporter, "hasFallback");
        hasFallback.set(true);
    }

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

    private Path fallbackDirForContext() {
        return tempDir.resolve(context.getOrganizationId())
                .resolve(context.getEnvironmentId())
                .resolve(context.getDomainId());
    }

    /** Waits until the primary {@code *.b64} fallback file has at least {@code minLines} non-blank lines (async I/O). */
    private void awaitFallbackLineCountAtLeast(int minLines) throws Exception {
        Path fallbackDir = fallbackDirForContext();
        long deadline = System.currentTimeMillis() + FALLBACK_ASSERT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (Files.isDirectory(fallbackDir)) {
                long n = countNonBlankLinesInFirstB64File(fallbackDir);
                if (n >= minLines) {
                    return;
                }
            }
            Thread.sleep(FALLBACK_POLL_INTERVAL_MS);
        }
        long n = Files.isDirectory(fallbackDir) ? countNonBlankLinesInFirstB64File(fallbackDir) : 0;
        assertThat(n).as("non-blank lines in fallback *.b64 (expected at least %d)", minLines).isGreaterThanOrEqualTo(minLines);
    }

    /** {@link TcpAuditReporter#restoreHasFallbackFromDisk()} completes on the Vert.x event loop — poll the flag. */
    private void awaitHasFallbackTrue() throws Exception {
        long deadline = System.currentTimeMillis() + FALLBACK_ASSERT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            AtomicBoolean hf = (AtomicBoolean) ReflectionTestUtils.getField(reporter, "hasFallback");
            if (hf.get()) {
                return;
            }
            Thread.sleep(FALLBACK_POLL_INTERVAL_MS);
        }
        AtomicBoolean hf = (AtomicBoolean) ReflectionTestUtils.getField(reporter, "hasFallback");
        assertThat(hf.get()).as("hasFallback after restoreHasFallbackFromDisk").isTrue();
    }

    private void awaitPathAbsent(Path path) throws Exception {
        long deadline = System.currentTimeMillis() + FALLBACK_ASSERT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (!Files.exists(path)) {
                return;
            }
            Thread.sleep(FALLBACK_POLL_INTERVAL_MS);
        }
        assertThat(path).as("path should be removed after drain").doesNotExist();
    }

    /**
     * Asserts the active TCP fallback file eventually contains {@code expectedCount} non-blank lines.
     */
    private void assertFallbackFileContainsEntries(int expectedCount) throws Exception {
        Path fallbackDir = fallbackDirForContext();

        long deadline = System.currentTimeMillis() + FALLBACK_ASSERT_TIMEOUT_MS;
        long lineCount = 0;
        while (System.currentTimeMillis() < deadline) {
            if (Files.isDirectory(fallbackDir)) {
                lineCount = countNonBlankLinesInFirstB64File(fallbackDir);
                if (lineCount == expectedCount) {
                    return;
                }
                if (lineCount > expectedCount) {
                    break;
                }
            }
            Thread.sleep(FALLBACK_POLL_INTERVAL_MS);
        }

        assertThat(fallbackDir).as("fallback directory should exist").exists();
        assertThat(lineCount).as("non-blank line count in first *.b64 under fallback dir").isEqualTo(expectedCount);
    }

    private long countNonBlankLinesInFirstB64File(Path fallbackDir) throws IOException {
        try (var paths = Files.list(fallbackDir)) {
            return paths
                    .filter(p -> p.toString().endsWith(".b64"))
                    .findFirst()
                    .map(p -> {
                        try {
                            try (var lines = Files.lines(p)) {
                                return lines.filter(l -> !l.isBlank()).count();
                            }
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .orElse(0L);
        }
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

    /**
     * Returns the path that TcpAuditReporter will use for its fallback file, derived using
     * the same formula as {@code resolveFallbackFile()} so tests can pre-populate it.
     */
    private Path expectedFallbackFilePath() throws IOException {
        Path dir = tempDir
                .resolve(context.getOrganizationId())
                .resolve(context.getEnvironmentId())
                .resolve(context.getDomainId());
        Files.createDirectories(dir);
        String fileId = "tcp-fallback-" + Math.abs(Objects.hash(
                defaultConfig().getHost(), defaultConfig().getPort()));
        return dir.resolve(fileId + ".b64");
    }
}
