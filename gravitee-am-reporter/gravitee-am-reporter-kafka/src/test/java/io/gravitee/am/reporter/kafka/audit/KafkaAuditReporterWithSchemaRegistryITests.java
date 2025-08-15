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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.common.utils.WriteStreamRegistry;
import io.gravitee.am.reporter.kafka.AuditValueFactory;
import io.gravitee.am.reporter.kafka.JUnitConfiguration;
import io.gravitee.am.reporter.kafka.KafkaReporterConfiguration;
import io.gravitee.am.reporter.kafka.SchemaRegistryContainer;
import io.gravitee.am.reporter.kafka.dto.AuditMessageValueDto;
import io.gravitee.node.api.Node;
import io.vertx.core.Vertx;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.testcontainers.containers.KafkaContainer;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JUnitConfiguration.class, loader = AnnotationConfigContextLoader.class)
public class KafkaAuditReporterWithSchemaRegistryITests {

    private KafkaAuditReporter kafkaAuditReporter;

    @Autowired
    protected ApplicationContext context;

    @Autowired
    protected GraviteeContext graviteeContext;

    @Autowired
    protected KafkaContainer kafkaContainer;

    @Autowired
    protected SchemaRegistryContainer schemaRegistryContainer;

    @Autowired
    private WriteStreamRegistry writeStreamRegistry;

    @Autowired
    protected Vertx vertx;

    @Autowired
    protected Node node;

    @Autowired
    @Qualifier("configWithSchemaRegistry")
    protected KafkaReporterConfiguration configuration;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void init() throws Exception {
        this.kafkaAuditReporter = new KafkaAuditReporter(configuration, vertx, graviteeContext, writeStreamRegistry, node);

        context.getAutowireCapableBeanFactory().autowireBean(kafkaAuditReporter);

        this.kafkaAuditReporter.start();
    }

    @Test
    void shouldWriteEventAsJson_When_EventIsAudit_WithSchemaRegistry() throws Exception {
        this.kafkaAuditReporter.report(AuditValueFactory.createAudit());

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, this.kafkaContainer.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "junit-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        String schemaRegistryUrl = "http://" + schemaRegistryContainer.getHost() + ":" + schemaRegistryContainer.getFirstMappedPort();
        config.put("schema.registry.url", schemaRegistryUrl);

        Deserializer<String> keyDeserializer = new StringDeserializer();
        KafkaJsonSchemaDeserializer<AuditMessageValueDto> valueDeserializer = getDeserializer();
        valueDeserializer.configure(config, false);

        KafkaConsumer<String, AuditMessageValueDto> kafkaConsumer = new KafkaConsumer<>(config, keyDeserializer, valueDeserializer);
        kafkaConsumer.subscribe(Collections.singletonList(this.configuration.getTopic()));

        ConsumerRecords<String, AuditMessageValueDto> poll = kafkaConsumer.poll(Duration.of(10, ChronoUnit.SECONDS));
        assertNotNull(poll);
        assertEquals(1, poll.count());

        ConsumerRecord<String, AuditMessageValueDto> record = poll.iterator().next();

        SchemaRegistryClient client = new CachedSchemaRegistryClient(schemaRegistryUrl, 10);
        Collection<String> allSubjects = client.getAllSubjects();
        assertEquals(1, allSubjects.size());
        SchemaMetadata schemaMetadata = client.getSchemaMetadata(allSubjects.iterator().next(), 1);
        assertNotNull(schemaMetadata);
        URL schema = this.getClass().getResource("/json/schema.json");
        byte[] expectedSchema = Files.readAllBytes(Paths.get(Objects.requireNonNull(schema).toURI()));
        String flattenedExpectedSchema = mapper.writeValueAsString(mapper.readValue(expectedSchema, Object.class));
        assertEquals(flattenedExpectedSchema, schemaMetadata.getSchema());

        assertNotNull(record.value());
    }

    private KafkaJsonSchemaDeserializer<AuditMessageValueDto> getDeserializer() {
        KafkaJsonSchemaDeserializer<AuditMessageValueDto> objectKafkaJsonSchemaDeserializer = new KafkaJsonSchemaDeserializer<>();
        ObjectMapper mapper = objectKafkaJsonSchemaDeserializer.objectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectKafkaJsonSchemaDeserializer;
    }
}
