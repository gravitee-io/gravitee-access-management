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
package io.gravitee.am.reporter.kafka;

import io.gravitee.am.reporter.kafka.audit.KafkaAuditReporter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


class KafkaReporterUTests {

  @Test
  void Should_ReturnKafkaReporterConfiguration() {
    KafkaReporter reporter = new KafkaReporter();
    assertEquals(KafkaReporterConfiguration.class, reporter.configuration());
  }

  @Test
  void Should_ReturnKafkaAuditReporter() {
    KafkaReporter reporter = new KafkaReporter();
    assertEquals(KafkaAuditReporter.class, reporter.provider());
  }
}
