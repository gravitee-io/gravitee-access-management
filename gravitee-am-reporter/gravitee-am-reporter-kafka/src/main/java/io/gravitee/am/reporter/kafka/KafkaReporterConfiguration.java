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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.gravitee.am.reporter.api.ReporterConfiguration;
import io.gravitee.secrets.api.annotation.Secret;

import java.util.Set;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Florent Amaridon
 * @author Visiativ
 */
@Setter
@Getter
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@EqualsAndHashCode
public class KafkaReporterConfiguration implements ReporterConfiguration {

  private String bootstrapServers;
  private String topic;
  private String acks;
  private String username;
  @Secret
  private String password;
  private String schemaRegistryUrl;
  private Set<String> auditTypes = Set.of();
  private List<Map<String, String>> additionalProperties;

  /**
   * This hash is used to reuse KafkaProducers for multiple reporters (e.g. for AM users who have a lot of domains).
   * It's based on fields that identify single kafka Broker (or single connection to the broker, or single producer...)
   * It should not include fields like topic or auditTypes.
   *
   * @return hash calculated from "static" fields, used to map multiple reporters to a single kafka producer instance.
   */
  public String hash() {
    return "hash-" + Objects.hash(bootstrapServers, acks, username, password, schemaRegistryUrl, additionalProperties);
  }

}
