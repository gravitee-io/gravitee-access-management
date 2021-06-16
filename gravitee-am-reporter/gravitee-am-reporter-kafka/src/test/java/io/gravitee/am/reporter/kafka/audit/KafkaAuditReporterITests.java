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

import io.gravitee.am.reporter.kafka.AuditValueFactory;
import io.gravitee.am.reporter.kafka.JUnitConfiguration;
import io.gravitee.am.reporter.kafka.KafkaReporterConfiguration;
import io.gravitee.am.reporter.kafka.kafka.JacksonSerializerUTests;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import junit.framework.TestCase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.testcontainers.containers.KafkaContainer;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = JUnitConfiguration.class, loader = AnnotationConfigContextLoader.class)
public class KafkaAuditReporterITests extends TestCase {

  private KafkaAuditReporter kafkaAuditReporter;

  @Autowired
  protected ApplicationContext context;

  @Autowired
  protected KafkaContainer kafkaContainer;
  @Autowired
  protected KafkaReporterConfiguration kafkaReporterConfiguration;

  @Before
  public void init() throws Exception {
    this.kafkaAuditReporter = new KafkaAuditReporter();

    context.getAutowireCapableBeanFactory().autowireBean(kafkaAuditReporter);

    this.kafkaAuditReporter.afterPropertiesSet();
    this.kafkaAuditReporter.start();
  }

  @Test
  public void Should_ReturnFalse_When_CanSearchIsCalled() {
    assertFalse(this.kafkaAuditReporter.canSearch());
  }

  @Test
  public void Should_ReturnTrue_When_CanHandleReportableIsAnAudit() {
    assertTrue(this.kafkaAuditReporter.canHandle(AuditValueFactory.createAudit()));
  }

  @Test
  public void Should_ReturnFalse_When_CanHandleReportableIsNotAnAudit() {
    assertTrue(this.kafkaAuditReporter.canHandle(AuditValueFactory.createAudit()));
  }

  @Test
  public void Should_WriteEventAsJson_When_EventIsAudit() throws URISyntaxException, IOException {


    this.kafkaAuditReporter.report(AuditValueFactory.createAudit());


    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, this.kafkaContainer.getBootstrapServers());
    config.put(ConsumerConfig.GROUP_ID_CONFIG, "junit-" + UUID.randomUUID());
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    Deserializer<String> keyDeserializer = new StringDeserializer();
    Deserializer<String> valueDeserializer = new StringDeserializer();
    KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(config, keyDeserializer,
        valueDeserializer);
    kafkaConsumer.subscribe(Collections.singletonList(this.kafkaReporterConfiguration.getTopic()));
    ConsumerRecords<String, String> poll = kafkaConsumer.poll(Duration.of(10, ChronoUnit.SECONDS));
    assertNotNull(poll);
    assertEquals(1, poll.count());

    ConsumerRecord<String, String> record = poll.iterator().next();

    URL resource = JacksonSerializerUTests.class.getResource("/json/audit.json");
    byte[] expected = Files.readAllBytes(Paths.get(Objects.requireNonNull(resource).toURI()));
    assertEquals(new String(expected, StandardCharsets.UTF_8),
        record.value());
  }



}