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

import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.common.utils.WriteStreamRegistry;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.tcp.TcpReporterConfiguration;
import io.gravitee.am.reporter.tcp.client.TcpWriteStream;
import io.gravitee.am.reporter.tcp.formatter.Formatter;
import io.gravitee.am.reporter.tcp.formatter.FormatterFactory;
import io.gravitee.am.reporter.tcp.spring.TcpReporterSpringConfiguration;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.Node;
import io.gravitee.reporter.api.Reportable;
import io.gravitee.reporter.api.Reporter;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * TCP audit reporter.
 *
 * <p>Connection settings (host, port, timeouts, output format, SSL) are read from the
 * {@link TcpReporterConfiguration} bean, which is populated by the management console and
 * stored in the database. Fallback-file settings are the only properties still read from
 * {@code gravitee.yaml} via Spring's {@link Environment}.</p>
 *
 * <h3>Fallback configuration ({@code gravitee.yaml} only)</h3>
 * <pre>
 * reporters:
 *   tcp:
 *     fallback:
 *       enabled:   false
 *       directory: ${gravitee.home}/audit-logs/tcp-fallback/
 *       maxSize:   10485760   # bytes before rotation (default 10 MB)
 *       maxFiles:  5          # max fallback files kept (active + gzip archives)
 * </pre>
 *
 * <p>Multiple reporter instances pointing to the same TCP endpoint share a single
 * {@link TcpWriteStream} through the {@link WriteStreamRegistry}. Each instance keeps
 * its own fallback file (unique per reporter context).</p>
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Import(TcpReporterSpringConfiguration.class)
public class TcpAuditReporter extends AbstractService<Reporter> implements AuditReporter, InitializingBean {

    // -------------------------------------------------------------------------
    // Property keys — fallback settings only (read from gravitee.yaml)
    // -------------------------------------------------------------------------

    static final String PROP_FALLBACK_DIR         = "reporters.tcp.fallback.directory";
    static final String PROP_FALLBACK_DIR_ENABLED = "reporters.tcp.fallback.enabled";
    static final String PROP_FALLBACK_MAX_SIZE_IN_MB = "reporters.tcp.fallback.maxSize";
    static final String PROP_FALLBACK_MAX_FILES   = "reporters.tcp.fallback.maxFiles";

    private static final int DEFAULT_FALLBACK_MAX_SIZE_IN_MB = 10;
    private static final int  DEFAULT_FALLBACK_MAX_FILES = 5;

    // Keystore / truststore type constants
    private static final String KS_TYPE_JKS     = "jks";
    private static final String KS_TYPE_PKCS12  = "pkcs12";
    private static final String KS_TYPE_PEM     = "pem";

    private static final byte[] END_OF_LINE = new byte[]{'\r', '\n'};

    // -------------------------------------------------------------------------
    // Injected beans
    // -------------------------------------------------------------------------

    @Autowired
    private TcpReporterConfiguration config;

    @Autowired
    private Environment environment;  // used only for fallback settings

    @Autowired
    private Vertx vertx;

    @Autowired(required = false)
    private GraviteeContext context;

    @Autowired
    private WriteStreamRegistry writeStreamRegistry;

    @Autowired(required = false)
    private Node node;

    // -------------------------------------------------------------------------
    // Runtime state
    // -------------------------------------------------------------------------

    private TcpWriteStream tcpWriteStream;

    @SuppressWarnings("rawtypes")
    private Formatter formatter;

    private String fallbackFile;
    private boolean fallbackFileEnabled;

    private final AtomicBoolean hasFallback = new AtomicBoolean(false);
    private final AtomicBoolean draining   = new AtomicBoolean(false);

    private final Handler<Void> onReconnectHandler = v -> onReconnect();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void afterPropertiesSet() {
        // context may be null when used outside a domain scope
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // Resolve formatter
        io.gravitee.am.reporter.tcp.formatter.Type type =
                io.gravitee.am.reporter.tcp.formatter.Type.valueOf(config.getOutput().toUpperCase(Locale.ENGLISH));
        formatter = FormatterFactory.getFormatter(type);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(formatter);

        // Resolve fallback file
        fallbackFile = resolveFallbackFile(type);
        fallbackFileEnabled = environment.getProperty(PROP_FALLBACK_DIR_ENABLED, Boolean.class, false);
        log.info("TCP reporter fallback file: {}", fallbackFile);

        // Get or create the shared TcpWriteStream
        String streamKey = buildStreamKey();
        tcpWriteStream = (TcpWriteStream) writeStreamRegistry.getOrCreate(
                streamKey,
                () -> {
                    TcpWriteStream stream = new TcpWriteStream(
                            vertx,
                            config.getHost(),
                            config.getPort(),
                            buildNetClientOptions(),
                            config.getReconnectAttempts(),
                            config.getReconnectInterval(),
                            config.getRetryTimeout());
                    stream.initialize();
                    return stream;
                });


        tcpWriteStream.addReconnectHandler(onReconnectHandler);

        log.info("TCP reporter started → {}:{} (context={})",
                tcpWriteStream.getHost(), tcpWriteStream.getPort(), context);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (tcpWriteStream != null) {
            tcpWriteStream.removeReconnectHandler(onReconnectHandler);
            writeStreamRegistry.decreaseUsage(buildStreamKey()).ifPresent(stream -> {
                if (stream instanceof TcpWriteStream s) {
                    s.close();
                }
            });
            tcpWriteStream = null;
        }
        log.info("TCP reporter stopped (context={})", context);
    }

    // -------------------------------------------------------------------------
    // AuditReporter
    // -------------------------------------------------------------------------

    @Override
    public boolean canHandle(Reportable reportable) {
        return reportable instanceof Audit;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void report(Reportable reportable) {
        log.debug("report({})", reportable);
        if (!(reportable instanceof Audit audit)) {
            log.debug("Ignoring reportable of type {}", reportable.getClass().getName());
            return;
        }

        AuditEntry entry = toAuditEntry(audit);
        Buffer payload = formatter.format(entry);
        if (payload == null) {
            log.warn("Formatter returned null for audit id={}, skipping", audit.getId());
            return;
        }

        Buffer frame = payload.appendBytes(END_OF_LINE);

        TcpWriteStream stream = tcpWriteStream;
        if (stream != null && stream.isConnected()) {
            stream.write(frame)
                    .onSuccess(v -> {
                        if (hasFallback.get()) {
                            drainFallbackAsync();
                        }
                    })
                    .onFailure(err -> {
                        log.warn("Failed to send audit id={} over TCP ({}:{}) — writing to fallback",
                                audit.getId(), stream.getHost(), stream.getPort(), err);
                        writeFallback(payload);
                    });
        } else if (fallbackFileEnabled) {
            log.warn("TCP not connected — writing audit id={} to fallback file", audit.getId());
            writeFallback(payload);
        } else {
            log.warn("TCP not connected — audit id={} has been lost", audit.getId());
        }
    }

    @Override
    public boolean canSearch() {
        return false;
    }

    @Override
    public Single<Page<Audit>> search(ReferenceType referenceType, String referenceId,
                                      AuditReportableCriteria criteria, int page, int size) {
        throw new IllegalStateException("Search method not implemented for TCP reporter");
    }

    @Override
    public Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId,
                                                 AuditReportableCriteria criteria, Type analyticsType) {
        throw new IllegalStateException("Aggregate method not implemented for TCP reporter");
    }

    @Override
    public Maybe<Audit> findById(ReferenceType referenceType, String referenceId, String id) {
        throw new IllegalStateException("FindById method not implemented for TCP reporter");
    }

    // -------------------------------------------------------------------------
    // NetClientOptions builder
    // -------------------------------------------------------------------------

    /**
     * Builds a fully-configured {@link NetClientOptions} from the {@link TcpReporterConfiguration}.
     * TLS (keystore and truststore) is configured when {@code ssl.enabled=true}. Certificate and
     * key material is supplied via {@code setValue} / {@code addCertValue} / {@code addKeyValue}
     * (in-memory content) rather than file paths.
     */
    NetClientOptions buildNetClientOptions() {
        NetClientOptions options = new NetClientOptions()
                .setConnectTimeout(config.getConnectTimeout())
                // Vert.x built-in reconnect is disabled; we manage reconnects ourselves
                .setReconnectAttempts(0);

        TcpReporterConfiguration.SslConfiguration ssl = config.getSsl();
        if (ssl != null && ssl.isEnabled()) {
            options.setSsl(true);
            options.setTrustAll(ssl.isTrustAll());
            options.setHostnameVerificationAlgorithm(ssl.isVerifyHost() ? "HTTPS" : "");

            configureKeystore(options, ssl.getKeystore());
            configureTruststore(options, ssl.getTruststore());
        }

        return options;
    }

    private void configureKeystore(NetClientOptions options,
                                    TcpReporterConfiguration.KeyStoreConfiguration ks) {
        if (ks == null || !StringUtils.hasText(ks.getType())) {
            return;
        }
        switch (ks.getType().toLowerCase(Locale.ENGLISH)) {
            case KS_TYPE_JKS -> {
                JksOptions jks = new JksOptions();
                applyBase64Buffer(jks::setValue, ks.getValue());
                applyValue(jks::setPassword, ks.getPassword());
                options.setKeyCertOptions(jks);
            }
            case KS_TYPE_PKCS12 -> {
                PfxOptions pfx = new PfxOptions();
                applyBase64Buffer(pfx::setValue, ks.getValue());
                applyValue(pfx::setPassword, ks.getPassword());
                options.setKeyCertOptions(pfx);
            }
            case KS_TYPE_PEM -> {
                PemKeyCertOptions pem = new PemKeyCertOptions();
                applyStringBuffer(pem::addCertValue, ks.getCertValue());
                applyStringBuffer(pem::addKeyValue, ks.getKeyValue());
                options.setKeyCertOptions(pem);
            }
            default -> log.warn("Unknown keystore type '{}', keystore not configured", ks.getType());
        }
    }

    private void configureTruststore(NetClientOptions options,
                                      TcpReporterConfiguration.TrustStoreConfiguration ts) {
        if (ts == null || !StringUtils.hasText(ts.getType())) {
            return;
        }
        switch (ts.getType().toLowerCase(Locale.ENGLISH)) {
            case KS_TYPE_JKS -> {
                JksOptions jks = new JksOptions();
                applyBase64Buffer(jks::setValue, ts.getValue());
                applyValue(jks::setPassword, ts.getPassword());
                options.setTrustOptions(jks);
            }
            case KS_TYPE_PKCS12 -> {
                PfxOptions pfx = new PfxOptions();
                applyBase64Buffer(pfx::setValue, ts.getValue());
                applyValue(pfx::setPassword, ts.getPassword());
                options.setTrustOptions(pfx);
            }
            case KS_TYPE_PEM -> {
                PemTrustOptions pem = new PemTrustOptions();
                applyStringBuffer(pem::addCertValue, ts.getCertValue());
                options.setTrustOptions(pem);
            }
            default -> log.warn("Unknown truststore type '{}', truststore not configured", ts.getType());
        }
    }

    // -------------------------------------------------------------------------
    // Fallback write
    // -------------------------------------------------------------------------

    private void writeFallback(Buffer payload) {
        hasFallback.set(true);
        String line = Base64.getEncoder().encodeToString(payload.getBytes()) + "\n";
        log.warn("Writing audit to fallback file: {}", fallbackFile);

        long maxSizeInBytes = environment.getProperty(PROP_FALLBACK_MAX_SIZE_IN_MB, Integer.class, DEFAULT_FALLBACK_MAX_SIZE_IN_MB) * 1024 * 1024;
        vertx.fileSystem().exists(fallbackFile)
                .onSuccess(exists -> {
                    if (!exists) {
                        appendToFallback(line);
                        return;
                    }
                    vertx.fileSystem().props(fallbackFile)
                            .onSuccess(props -> {
                                if (props.size() >= maxSizeInBytes) {
                                    rotateFallbackFile(() -> appendToFallback(line));
                                } else {
                                    appendToFallback(line);
                                }
                            })
                            .onFailure(err -> {
                                log.warn("Could not check fallback file size ({}), appending anyway: {}",
                                        fallbackFile, err.getMessage());
                                appendToFallback(line);
                            });
                })
                .onFailure(err -> appendToFallback(line));
    }

    private void appendToFallback(String line) {
        vertx.fileSystem()
                .open(fallbackFile, new OpenOptions().setAppend(true).setCreate(true))
                .onSuccess(asyncFile -> asyncFile.write(Buffer.buffer(line))
                        .onComplete(v -> asyncFile.close()))
                .onFailure(err -> log.error("Failed to write audit to fallback file {}", fallbackFile, err));
    }

    // -------------------------------------------------------------------------
    // Fallback drain — entry points
    // -------------------------------------------------------------------------

    private void onReconnect() {
        log.info("TCP connection (re-)established. Checking fallback file…");
        if (hasFallback.get()) {
            drainFallbackAsync();
        }
    }

    /**
     * Starts an asynchronous drain of all fallback files (archives first, then active), resuming
     * from the position saved by a previous interrupted drain when available.
     */
    private void drainFallbackAsync() {
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        buildFileList()
                .onSuccess(files -> {
                    if (files.isEmpty()) {
                        hasFallback.set(false);
                        draining.set(false);
                        return;
                    }
                    loadProgress(files)
                            .onSuccess(state -> drainNextFile(files, (int) state[0], state[1]))
                            .onFailure(err -> drainNextFile(files, 0, 0));
                })
                .onFailure(err -> {
                    log.warn("Failed to enumerate fallback files: {}", err.getMessage());
                    draining.set(false);
                });
    }

    // -------------------------------------------------------------------------
    // Fallback drain — multi-file sequencing
    // -------------------------------------------------------------------------

    /**
     * Drains {@code files} in order (oldest first). {@code linesToSkip} applies only to the
     * file at {@code index} and represents lines already forwarded in a prior drain attempt.
     * Each file is deleted after it is fully drained before moving to the next.
     */
    private void drainNextFile(List<Path> files, int index, long linesToSkip) {
        if (index >= files.size()) {
            log.info("All fallback files drained successfully");
            deleteProgressFile();
            hasFallback.set(false);
            draining.set(false);
            return;
        }

        Path current = files.get(index);
        // After a file is fully drained: delete it and advance to the next
        Runnable onComplete = () -> vertx.fileSystem().delete(current.toString())
                .onComplete(v -> drainNextFile(files, index + 1, 0));

        if (current.toString().endsWith(".gz")) {
            drainGzipFile(current, linesToSkip, files, index, onComplete);
        } else {
            openAndStreamDrain(current, linesToSkip, files, index, onComplete);
        }
    }

    /**
     * Decompresses a {@code .b64.gz} archive to a temporary {@code .b64.tmp} file on a Vert.x
     * worker thread, then delegates to {@link #openAndStreamDrain}. The temp file is always
     * deleted after the drain attempt (success or failure).
     */
    private void drainGzipFile(Path gzFile, long linesToSkip,
                                 List<Path> files, int fileIndex, Runnable onComplete) {
        Path tempFile = gzFile.getParent()
                .resolve(gzFile.getFileName().toString().replace(".gz", ".tmp"));

        vertx.<Void>executeBlocking(() -> {
            try (GZIPInputStream gzip = new GZIPInputStream(Files.newInputStream(gzFile));
                 OutputStream out = Files.newOutputStream(tempFile)) {
                gzip.transferTo(out);
            }
            return null;
        })
        .onSuccess(v -> {
            Runnable afterTemp = () -> vertx.fileSystem().delete(tempFile.toString())
                    .onComplete(v2 -> onComplete.run());
            openAndStreamDrain(tempFile, linesToSkip, files, fileIndex, afterTemp);
        })
        .onFailure(err -> {
            log.error("Failed to decompress fallback archive {}, skipping", gzFile, err);
            vertx.fileSystem().exists(tempFile.toString())
                    .onSuccess(exists -> { if (exists) vertx.fileSystem().delete(tempFile.toString()); });
            drainNextFile(files, fileIndex + 1, 0);
        });
    }

    private void openAndStreamDrain(Path file, long linesToSkip,
                                     List<Path> files, int fileIndex, Runnable onComplete) {
        vertx.fileSystem().open(file.toString(), new OpenOptions().setRead(true))
                .onSuccess(asyncFile -> streamDrain(asyncFile, file, linesToSkip, files, fileIndex, onComplete))
                .onFailure(err -> {
                    log.warn("Failed to open fallback file {}: {}", file, err.getMessage());
                    draining.set(false);
                });
    }

    // -------------------------------------------------------------------------
    // Fallback drain — streaming core
    // -------------------------------------------------------------------------

    /**
     * Streams {@code file} line by line through a {@link RecordParser}. The first
     * {@code linesToSkip} lines are consumed from the file but not forwarded to TCP — they
     * represent entries already delivered in a previous drain attempt. The async file is paused
     * after each batch of lines and only resumed once all pending TCP writes for that batch
     * complete, so the file is never fully loaded into memory.
     *
     * <p>On TCP failure the current position ({@code linesRead}) is persisted to a progress
     * file so the next reconnect can skip already-delivered lines exactly. {@code onComplete}
     * is invoked (instead of deleting the file) so the caller can manage file lifecycle.</p>
     */
    private void streamDrain(AsyncFile asyncFile, Path filePath, long linesToSkip,
                              List<Path> files, int fileIndex, Runnable onComplete) {
        TcpWriteStream stream = tcpWriteStream;
        if (stream == null || !stream.isConnected()) {
            asyncFile.close();
            draining.set(false);
            return;
        }

        AtomicBoolean aborted   = new AtomicBoolean(false);
        AtomicLong    linesRead = new AtomicLong(0);   // total lines seen (skipped + sent)
        AtomicLong    pending   = new AtomicLong(0);
        AtomicBoolean fileEnded = new AtomicBoolean(false);

        RecordParser parser = RecordParser.newDelimited("\n", lineBuffer -> {
            if (aborted.get()) return;

            String trimmed = lineBuffer.toString().trim();
            if (trimmed.isEmpty()) return;

            long lineIndex = linesRead.incrementAndGet();

            // Skip lines already delivered in a prior drain attempt
            if (lineIndex <= linesToSkip) {
                return;
            }

            pending.incrementAndGet();
            asyncFile.pause();

            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(trimmed);
            } catch (IllegalArgumentException e) {
                log.warn("Skipping invalid base64 entry (line {}) in {}", lineIndex, filePath);
                resumeOrFinish(asyncFile, pending, linesRead, fileEnded, aborted,
                        filePath, files, fileIndex, onComplete);
                return;
            }

            TcpWriteStream currentStream = tcpWriteStream;
            if (currentStream == null || !currentStream.isConnected()) {
                if (aborted.compareAndSet(false, true)) {
                    log.warn("TCP connection lost during fallback drain of {} at line {}", filePath, lineIndex);
                    asyncFile.close();
                    saveProgress(filePath, linesRead.get());
                    draining.set(false);
                }
                return;
            }

            currentStream.write(Buffer.buffer(decoded).appendBytes(END_OF_LINE))
                    .onSuccess(v -> resumeOrFinish(asyncFile, pending, linesRead, fileEnded, aborted,
                            filePath, files, fileIndex, onComplete))
                    .onFailure(err -> {
                        if (aborted.compareAndSet(false, true)) {
                            log.warn("Fallback drain interrupted at line {} of {} — saving progress for retry",
                                    linesRead.get(), filePath);
                            asyncFile.close();
                            saveProgress(filePath, linesRead.get());
                            draining.set(false);
                        }
                    });
        });

        asyncFile
                .handler(parser)
                .endHandler(v -> {
                    fileEnded.set(true);
                    if (pending.get() == 0 && !aborted.get()) {
                        finishFileDrain(asyncFile, filePath, linesRead.get(), onComplete);
                    }
                })
                .exceptionHandler(err -> {
                    log.warn("Error reading fallback file {}: {}", filePath, err.getMessage());
                    saveProgress(filePath, linesRead.get());
                    asyncFile.close();
                    draining.set(false);
                });
    }

    /**
     * Decrements the pending-write counter and either resumes file reading (more lines remain)
     * or finalises the file drain (file ended and all writes completed).
     */
    private void resumeOrFinish(AsyncFile asyncFile,
                                 AtomicLong pending, AtomicLong linesRead,
                                 AtomicBoolean fileEnded, AtomicBoolean aborted,
                                 Path filePath, List<Path> files, int fileIndex, Runnable onComplete) {
        if (pending.decrementAndGet() == 0 && !aborted.get()) {
            if (fileEnded.get()) {
                finishFileDrain(asyncFile, filePath, linesRead.get(), onComplete);
            } else {
                asyncFile.resume();
            }
        }
    }

    private void finishFileDrain(AsyncFile asyncFile, Path filePath, long linesRead, Runnable onComplete) {
        log.info("Fallback file {} fully drained ({} lines)", filePath, linesRead);
        asyncFile.close();
        onComplete.run();  // caller deletes the file and advances to drainNextFile
    }

    // -------------------------------------------------------------------------
    // File rotation and pruning
    // -------------------------------------------------------------------------

    /**
     * Compresses the current fallback file with gzip on a worker thread, deletes the original,
     * then prunes archives if the total count exceeds {@code reporters.tcp.fallback.maxFiles}.
     * Archived files follow the naming pattern {@code tcp-fallback-{hash}-{timestamp}.b64.gz}.
     */
    private void rotateFallbackFile(Runnable afterRotate) {
        String archivePath = fallbackFile.replace(".b64",
                "-" + System.currentTimeMillis() + ".b64.gz");
        log.info("Rotating fallback file {} → {}", fallbackFile, archivePath);

        vertx.<Void>executeBlocking(() -> {
            Path src = Paths.get(fallbackFile);
            Path dst = Paths.get(archivePath);
            try (InputStream in = Files.newInputStream(src);
                 GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(dst))) {
                in.transferTo(gzip);
            }
            Files.delete(src);
            return null;
        })
        .onSuccess(v -> {
            log.info("Fallback file rotated to {}", archivePath);
            pruneOldFallbackFiles();
            if (afterRotate != null) afterRotate.run();
        })
        .onFailure(err -> {
            log.error("Failed to rotate fallback file {}", fallbackFile, err);
            if (afterRotate != null) afterRotate.run();
        });
    }

    /**
     * Deletes the oldest fallback files (active + gzip archives) when the total count exceeds
     * {@code reporters.tcp.fallback.maxFiles} (default {@value DEFAULT_FALLBACK_MAX_FILES}).
     * Progress and temporary files are excluded from the count and never pruned.
     */
    private void pruneOldFallbackFiles() {
        int maxFiles = environment.getProperty(PROP_FALLBACK_MAX_FILES, Integer.class, DEFAULT_FALLBACK_MAX_FILES);
        Path dir     = Paths.get(fallbackFile).getParent();
        String fileId = Paths.get(fallbackFile).getFileName().toString().replace(".b64", "");

        vertx.<List<Path>>executeBlocking(() -> {
            try (Stream<Path> entries = Files.list(dir)) {
                return entries
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return name.startsWith(fileId)
                                    && (name.endsWith(".b64") || name.endsWith(".b64.gz"));
                        })
                        .sorted((a, b) -> {
                            try {
                                return Long.compare(
                                        Files.getLastModifiedTime(a).toMillis(),
                                        Files.getLastModifiedTime(b).toMillis());
                            } catch (IOException ex) {
                                return 0;
                            }
                        })
                        .collect(Collectors.toList());
            }
        })
        .onSuccess(files -> {
            if (files.size() <= maxFiles) return;
            int toDelete = files.size() - maxFiles;
            log.info("Pruning {} old fallback file(s) (limit={}, found={})", toDelete, maxFiles, files.size());
            for (int i = 0; i < toDelete; i++) {
                Path victim = files.get(i);
                vertx.fileSystem().delete(victim.toString())
                        .onSuccess(v -> log.info("Pruned fallback file: {}", victim))
                        .onFailure(e -> log.warn("Failed to prune {}: {}", victim, e.getMessage()));
            }
        })
        .onFailure(err -> log.warn("Failed to list fallback directory for pruning: {}", err.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Progress file helpers
    // -------------------------------------------------------------------------

    /** Returns the path of the drain-progress marker file for this reporter. */
    private String progressFile() {
        return fallbackFile.replace(".b64", ".progress");
    }

    /**
     * Builds an ordered list of all fallback files (archives + active, oldest first) that
     * belong to this reporter. Progress ({@code .progress}) and temp ({@code .tmp}) files
     * are excluded.
     */
    private Future<List<Path>> buildFileList() {
        Path dir     = Paths.get(fallbackFile).getParent();
        String fileId = Paths.get(fallbackFile).getFileName().toString().replace(".b64", "");

        return vertx.<List<Path>>executeBlocking(() -> {
            if (!Files.isDirectory(dir)) return List.of();
            try (Stream<Path> entries = Files.list(dir)) {
                return entries
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return name.startsWith(fileId)
                                    && (name.endsWith(".b64") || name.endsWith(".b64.gz"));
                        })
                        .sorted((a, b) -> {
                            try {
                                return Long.compare(
                                        Files.getLastModifiedTime(a).toMillis(),
                                        Files.getLastModifiedTime(b).toMillis());
                            } catch (IOException ex) {
                                return 0;
                            }
                        })
                        .collect(Collectors.toList());
            }
        });
    }

    /**
     * Reads the progress file (if it exists) and returns {@code [fileIndex, linesConsumed]}.
     * If no progress is saved or the referenced file is no longer present in {@code files},
     * returns {@code [0, 0]} so the drain starts from the beginning.
     */
    private Future<long[]> loadProgress(List<Path> files) {
        String prog = progressFile();
        return vertx.fileSystem().exists(prog)
                .compose(exists -> {
                    if (!exists) return Future.succeededFuture(new long[]{0, 0});
                    return vertx.fileSystem().readFile(prog)
                            .map(content -> {
                                String[] parts = content.toString().trim().split("\n", 2);
                                if (parts.length < 2) return new long[]{0, 0};
                                String savedPath = parts[0].trim();
                                long savedLines;
                                try {
                                    savedLines = Long.parseLong(parts[1].trim());
                                } catch (NumberFormatException e) {
                                    return new long[]{0, 0};
                                }
                                for (int i = 0; i < files.size(); i++) {
                                    if (files.get(i).toString().equals(savedPath)) {
                                        log.info("Resuming fallback drain of {} from line {}", savedPath, savedLines);
                                        return new long[]{i, savedLines};
                                    }
                                }
                                log.warn("Progress file references unknown path {}, starting over", savedPath);
                                return new long[]{0, 0};
                            });
                });
    }

    /**
     * Persists the drain position so the next reconnect can resume without re-delivering
     * entries. Format: {@code {absoluteFilePath}\n{linesConsumed}}.
     */
    private void saveProgress(Path filePath, long linesConsumed) {
        String content = filePath.toString() + "\n" + linesConsumed;
        vertx.fileSystem().writeFile(progressFile(), Buffer.buffer(content))
                .onSuccess(v -> log.debug("Saved drain progress: {}:{}", filePath, linesConsumed))
                .onFailure(err -> log.warn("Failed to save drain progress: {}", err.getMessage()));
    }

    private void deleteProgressFile() {
        String prog = progressFile();
        vertx.fileSystem().exists(prog)
                .onSuccess(exists -> {
                    if (exists) {
                        vertx.fileSystem().delete(prog)
                                .onFailure(err -> log.warn("Failed to delete progress file {}: {}",
                                        prog, err.getMessage()));
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AuditEntry toAuditEntry(Audit audit) {
        AuditEntry entry = new AuditEntry();
        entry.setId(audit.getId());
        entry.setReferenceId(audit.getReferenceId());
        entry.setReferenceType(audit.getReferenceType());
        entry.setTimestamp(audit.timestamp());
        entry.setTransactionId(audit.getTransactionId());
        entry.setType(audit.getType());

        if (audit.getOutcome() != null) {
            entry.setOutcome(audit.getOutcome());
        }

        AuditAccessPoint accessPoint = audit.getAccessPoint();
        if (accessPoint != null) {
            AuditAccessPoint sanitised = new AuditAccessPoint(accessPoint);
            if (accessPoint.getIpAddress() != null
                    && !InetAddressValidator.getInstance().isValid(accessPoint.getIpAddress())) {
                sanitised.setIpAddress("0.0.0.0");
            }
            entry.setAccessPoint(sanitised);
        }

        AuditEntity actor = audit.getActor();
        if (actor != null) {
            AuditEntity copy = new AuditEntity(actor);
            copy.setAttributes(null);
            entry.setActor(copy);
        }

        AuditEntity target = audit.getTarget();
        if (target != null) {
            AuditEntity copy = new AuditEntity(target);
            copy.setAttributes(null);
            entry.setTarget(copy);
        }

        if (context != null) {
            entry.setOrganizationId(context.getOrganizationId());
            entry.setEnvironmentId(context.getEnvironmentId());
        }
        if (node != null) {
            entry.setNodeId(node.id());
            entry.setNodeHostname(node.hostname());
        }

        return entry;
    }

    private String resolveFallbackFile(io.gravitee.am.reporter.tcp.formatter.Type type) throws IOException {
        String defaultDir = System.getProperty("gravitee.home", ".") + "/audit-logs/tcp-fallback/";
        String baseDir = environment.getProperty(PROP_FALLBACK_DIR, defaultDir);

        Path dir = Paths.get(baseDir);
        if (context != null) {
            if (StringUtils.hasText(context.getOrganizationId())) dir = dir.resolve(context.getOrganizationId());
            if (StringUtils.hasText(context.getEnvironmentId()))  dir = dir.resolve(context.getEnvironmentId());
            if (StringUtils.hasText(context.getDomainId()))       dir = dir.resolve(context.getDomainId());
        }

        Files.createDirectories(dir);

        String fileId = "tcp-fallback-" + Math.abs(Objects.hash(
                config.getHost(), config.getPort(), context));
        return dir.resolve(fileId + ".b64").toAbsolutePath().toString();
    }

    /**
     * Returns the {@link WriteStreamRegistry} key for this reporter's TCP endpoint.
     * Reporters sharing the same host+port+SSL configuration reuse a single {@link TcpWriteStream}.
     * SSL settings are included so that two reporters pointing at the same address but with
     * different TLS parameters (e.g. different client certificates) get distinct streams.
     */
    String buildStreamKey() {
        TcpReporterConfiguration.SslConfiguration ssl = config.getSsl();
        if (ssl == null || !ssl.isEnabled()) {
            return "tcp-" + Objects.hash(config.getHost(), config.getPort(), false);
        }
        TcpReporterConfiguration.KeyStoreConfiguration ks = ssl.getKeystore();
        TcpReporterConfiguration.TrustStoreConfiguration ts = ssl.getTruststore();
        return "tcp-" + Objects.hash(
                config.getHost(),
                config.getPort(),
                ssl.isTrustAll(),
                ssl.isVerifyHost(),
                ks != null ? ks.getType() : null,
                ks != null ? ks.getValue() : null,
                ks != null ? ks.getCertValue() : null,
                ts != null ? ts.getType() : null,
                ts != null ? ts.getValue() : null,
                ts != null ? ts.getCertValue() : null
        );
    }

    // -------------------------------------------------------------------------
    // Tiny helpers to avoid null-setting
    // -------------------------------------------------------------------------

    /** Decodes a base64 string and passes the resulting {@link Buffer} to {@code setter}. */
    private static void applyBase64Buffer(java.util.function.Consumer<Buffer> setter, String base64) {
        if (StringUtils.hasText(base64)) {
            setter.accept(Buffer.buffer(Base64.getDecoder().decode(base64)));
        }
    }

    /** Wraps a plain string (e.g. PEM content) in a {@link Buffer} and passes it to {@code setter}. */
    private static void applyStringBuffer(java.util.function.Consumer<Buffer> setter, String value) {
        if (StringUtils.hasText(value)) {
            setter.accept(Buffer.buffer(value));
        }
    }

    private static void applyValue(java.util.function.Consumer<String> setter, String value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
