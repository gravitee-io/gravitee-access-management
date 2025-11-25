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

import static io.gravitee.am.reporter.kafka.AuditValueFactory.createAuditOfType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.gravitee.am.reporter.kafka.KafkaReporterConfiguration;

import java.io.IOException;
import java.util.Set;

import java.util.UUID;

import org.junit.jupiter.api.Test;

public class KafkaAuditReporterUTests {

  @Test
  void Should_MatchAudit_ByType() throws IOException {
    // given: reporter that supports different audit types
    var config = new KafkaReporterConfiguration();
    config.setAuditTypes(Set.of("foo", "bar"));
    try (KafkaAuditReporter cut = new KafkaAuditReporter(config, mock(), mock(), mock(), mock())) {
      // expect: reporter should handle supported types
      assertThat(cut.canHandle(createAuditOfType("foo"))).isTrue();
      assertThat(cut.canHandle(createAuditOfType("bar"))).isTrue();

      // but: reporter should reject unsupported type
      assertThat(cut.canHandle(createAuditOfType("baz"))).isFalse();
    }
  }

  @Test
  void Should_Match_AnyType_When_EmptyAuditTypes() throws IOException {
    // given: reporter that supports any audit types (empty audit types set)
    var config = new KafkaReporterConfiguration();
    config.setAuditTypes(Set.of());
    try (KafkaAuditReporter cut = new KafkaAuditReporter(config, mock(), mock(), mock(), mock())) {

      // expect: reporter should handle any audit types
      assertThat(cut.canHandle(createAuditOfType(UUID.randomUUID().toString()))).isTrue();
    }
  }
}
