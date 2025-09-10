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
package io.gravitee.am.reporter.kafka.audit;

import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.common.utils.WriteStreamRegistry;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.kafka.KafkaReporterConfiguration;
import io.gravitee.am.reporter.kafka.dto.AuditMessageValueDto;
import io.gravitee.am.reporter.kafka.kafka.JacksonSerializer;
import io.gravitee.am.reporter.kafka.kafka.KafkaJsonSerializer;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.Node;
import io.gravitee.reporter.api.Reportable;
import io.gravitee.reporter.api.Reporter;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Florent Amaridon
 * @author Visiativ
 */
@Slf4j
public class KafkaAuditReporter extends AbstractService<Reporter> implements AuditReporter {

    private static final String SCHEMA_REGISTRY_URL_KEY = "schema.registry.url";

    private static final String SASL_JAAS_CONFIG_PLACEHOLDER = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";";


    private final Vertx vertx;

    private final KafkaReporterConfiguration config;


    private final GraviteeContext context;

    private final WriteStreamRegistry writeStreamRegistry;

    private final Node node;

    private KafkaProducer<String, AuditMessageValueDto> producer;

    public KafkaAuditReporter(KafkaReporterConfiguration config,
                              Vertx vertx,
                              GraviteeContext context,
                              WriteStreamRegistry writeStreamRegistry,
                              Node node) {
        this.config = config;
        this.vertx = vertx;
        this.context = context;
        this.node = node;
        this.writeStreamRegistry = writeStreamRegistry;
    }

    @Override
    public boolean canHandle(Reportable reportable) {
        return reportable instanceof Audit;
    }

    @Override
    public void report(Reportable reportable) {
        log.debug("Report({})", reportable);
        if (producer != null && reportable instanceof Audit audit) {
            // partition key computed on the audit referenceId which is either domainId or organizationId
            var key = audit.getReferenceId();
            if (audit.getReferenceType().equals(ReferenceType.ORGANIZATION) && audit.getType().startsWith(ReferenceType.DOMAIN.name())) {
                // for DOMAIN event linked to an organization as referenceId
                // route the event based on the domain id found into the target
                // in that way all even related to a single domain are in the same partition
                key = audit.getTarget().getId();
            }

            AuditMessageValueDto value = AuditMessageValueDto.from(audit, this.context, this.node);
            this.producer.write(
                    KafkaProducerRecord.create(this.config.getTopic(), key, value));
        } else {
            log.debug("Producer is null or reportable not an instance of Audit, ignore reportable");
        }
    }

    @Override
    protected void doStart() throws Exception{
        super.doStart();
        this.producer = (KafkaProducer) writeStreamRegistry.getOrCreate(config.hash(), this::createProducer);
    }

    private KafkaProducer createProducer() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return KafkaProducer.create(vertx, getProperties());
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        writeStreamRegistry.decreaseUsage(config.hash()).ifPresent(kafkaProducer -> {
            Producer<String, AuditMessageValueDto> producer = ((KafkaProducer) kafkaProducer).unwrap();
            Context ctx = vertx.getOrCreateContext();
            ctx.executeBlocking(() -> {
                producer.close();
                log.info("Kafka producer closed");
                return null;
            });
        });
        log.info("Kafka producer usage decreased");
    }

    @Override
    public boolean canSearch() {
        return false;
    }

    @Override
    public Single<Page<Audit>> search(ReferenceType referenceType, String referenceId,
                                      AuditReportableCriteria criteria, int page, int size) {
        throw new IllegalStateException("Search method not implemented for Kafka reporter");
    }

    @Override
    public Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId,
                                                 AuditReportableCriteria criteria, Type analyticsType) {
        throw new IllegalStateException("Aggregate method not implemented for Kafka reporter");
    }

    @Override
    public Maybe<Audit> findById(ReferenceType referenceType, String referenceId, String id) {
        throw new IllegalStateException("FindById method not implemented for Kafka reporter");
    }

    private Properties getProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, this.config.getBootstrapServers());
        properties.put(ProducerConfig.ACKS_CONFIG, this.config.getAcks());
        if (StringUtils.hasText(this.config.getSchemaRegistryUrl())) {
            properties.put(SCHEMA_REGISTRY_URL_KEY, this.config.getSchemaRegistryUrl());
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class);
        } else {
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonSerializer.class);
        }
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        if (StringUtils.hasText(config.getUsername()) && StringUtils.hasText(config.getPassword())) {
            properties.put(SaslConfigs.SASL_JAAS_CONFIG, String.format(SASL_JAAS_CONFIG_PLACEHOLDER, config.getUsername(), config.getPassword()));
        }

        List<Map<String, String>> additionalProperties = this.config.getAdditionalProperties();
        if (additionalProperties != null && !additionalProperties.isEmpty()) {
            additionalProperties.forEach(property -> {
                String option = property.get("option");
                String value = property.get("value");
                properties.put(option, value);
            });
        }

        Properties props = new Properties();
        props.putAll(properties);

        return props;
    }
}
