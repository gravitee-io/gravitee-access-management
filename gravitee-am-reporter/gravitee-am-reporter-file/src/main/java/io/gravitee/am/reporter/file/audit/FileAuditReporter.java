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

import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.file.FileReporterConfiguration;
import io.gravitee.am.reporter.file.exception.FileReporterInitializationException;
import io.gravitee.am.reporter.file.formatter.Formatter;
import io.gravitee.am.reporter.file.formatter.FormatterFactory;
import io.gravitee.am.reporter.file.spring.FileReporterSpringConfiguration;
import io.gravitee.am.reporter.file.vertx.VertxFileWriter;
import io.gravitee.common.service.AbstractService;
import io.gravitee.common.service.Service;
import io.gravitee.node.api.Node;
import io.gravitee.reporter.api.Reportable;
import io.gravitee.reporter.api.Reporter;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(FileReporterSpringConfiguration.class)
public class FileAuditReporter extends AbstractService<Reporter> implements AuditReporter, Service<Reporter>, InitializingBean {
    public static final String REPORTERS_FILE_ENABLED = "reporters.file.enabled";
    public static final String REPORTERS_FILE_DIRECTORY = "reporters.file.directory";
    public static final String REPORTERS_FILE_OUTPUT = "reporters.file.output";
    public static final String REPORTERS_FILE_RETAIN_DAYS = "reporters.file.retainDays";

    private static Logger LOGGER = LoggerFactory.getLogger(FileAuditReporter.class);

    @Value("${" + REPORTERS_FILE_DIRECTORY + ":#{systemProperties['gravitee.home']}/audit-logs/}")
    private String directory;

    @Value("${" + REPORTERS_FILE_OUTPUT + ":JSON}")
    private String outputType;

    @Value("${" + REPORTERS_FILE_RETAIN_DAYS + ":-1}")
    private long retainDays;

    @Autowired
    private Vertx vertx;

    @Autowired
    private FileReporterConfiguration config;

    @Autowired
    private GraviteeContext context;

    @Autowired
    private Node node;

    private VertxFileWriter<ReportEntry> writer;

    @Override
    public Single<Page<Audit>> search(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, int page, int size) {
        throw new IllegalStateException("Search method not implemented for File reporter");
    }

    @Override
    public Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, Type analyticsType) {
        throw new IllegalStateException("Aggregate method not implemented for File reporter");
    }

    @Override
    public Maybe<Audit> findById(ReferenceType referenceType, String referenceId, String id) {
        throw new IllegalStateException("FindById method not implemented for File reporter");
    }

    @Override
    public boolean canSearch() {
        return false;
    }

    @Override
    public void report(Reportable reportable) {
        LOGGER.debug("Report({})", reportable);
        if (writer != null) {
            if (reportable instanceof Audit audit) {
                AuditEntry entry = convert(audit);
                writer.write(entry);
            } else {
                LOGGER.debug("Ignore reportable of type {}", reportable.getClass().getName());
            }
        } else {
            LOGGER.debug("Writer is null, ignore reportable");
        }
    }

    private AuditEntry convert(Audit audit) {
        AuditEntry entry = new AuditEntry();
        entry.setId(audit.getId());
        entry.setReferenceId(audit.getReferenceId());
        entry.setReferenceType(audit.getReferenceType());
        entry.setTimestamp(audit.timestamp());
        entry.setTransactionId(audit.getTransactionId());
        entry.setType(audit.getType());

        // do not copy message part of the status

        var outcome = audit.getOutcome();
        if (outcome != null) {
            entry.setOutcome(outcome);
        }

        // copy access point and replace invalid IP
        AuditAccessPoint accessPoint = audit.getAccessPoint();
        if (accessPoint != null) {
            entry.setAccessPoint(new AuditAccessPoint(accessPoint));
            if (accessPoint.getIpAddress() != null && !InetAddressValidator.getInstance().isValid(accessPoint.getIpAddress())) {
                entry.getAccessPoint().setIpAddress("0.0.0.0");
            }
        }

        AuditEntity actor = audit.getActor();
        if (actor != null) {
            AuditEntity cloneOfActor = new AuditEntity(actor);
            cloneOfActor.setAttributes(null);
            entry.setActor(cloneOfActor);
        }

        AuditEntity target = audit.getTarget();
        if (target != null) {
            AuditEntity cloneOfTarget = new AuditEntity(target);
            cloneOfTarget.setAttributes(null);
            entry.setTarget(cloneOfTarget);
        }

        // link event to the organization and to the environment
        entry.setOrganizationId(context.getOrganizationId());
        entry.setEnvironmentId(context.getEnvironmentId());
        // add node information
        if (node != null) {
            entry.setNodeId(node.id());
            entry.setNodeHostname(node.hostname());
        }

        return entry;
    }

    @Override
    public boolean canHandle(Reportable reportable) {
        return true;
    }

    @Override
    public void afterPropertiesSet() {
        if (context == null) {
            context = GraviteeContext.defaultContext(null);
        }
    }

    @Override
    protected void doStart() throws Exception {

        // Initialize writers
        final io.gravitee.am.reporter.file.formatter.Type type = io.gravitee.am.reporter.file.formatter.Type.valueOf(outputType.toUpperCase(Locale.ENGLISH));
        Formatter<ReportEntry> formatter = FormatterFactory.getFormatter(type);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(formatter);

        String reporterDirectory = directory;
        if (context != null) {
            if (StringUtils.hasText(context.getOrganizationId())) {
                reporterDirectory = getOrCreateDirectory(reporterDirectory, context.getOrganizationId());
            }
            if (StringUtils.hasText(context.getEnvironmentId())) {
                reporterDirectory = getOrCreateDirectory(reporterDirectory, context.getEnvironmentId());
            }
            if (StringUtils.hasText(context.getDomainId())) {
                reporterDirectory = getOrCreateDirectory(reporterDirectory, context.getDomainId());
            }
        }

        final String filename = Paths.get(reporterDirectory, config.getFilename() + "-" + VertxFileWriter.YYYY_MM_DD + '.' + type.getExtension()).toFile().getAbsolutePath();
        long resolvedRetainDays = config.getRetainDays() > 0 ? config.getRetainDays() : this.retainDays;
        this.writer = new VertxFileWriter<>(
                vertx,
                formatter,
                filename,
                resolvedRetainDays);

        Future<Void> writerInitialization = writer.initialize();
        writerInitialization.onComplete(success -> LOGGER.info("File reporter successfully started"));
        writerInitialization.onFailure(error -> {
            LOGGER.warn("An error occurs while starting file reporter", error);
            throw new FileReporterInitializationException("An error occurs while starting file reporter [" + filename + "]");
        });
    }

    private String getOrCreateDirectory(String baseDirectory, String directoryName) throws IOException {
        Path dir = Paths.get(baseDirectory, directoryName);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        } else if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException(directoryName + " already exists but it is not a directory");
        }
        baseDirectory = dir.toFile().getAbsolutePath();
        return baseDirectory;
    }

}
