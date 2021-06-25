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

import java.util.List;
import java.util.Map;

/**
 * @author Florent Amaridon
 * @author Visiativ
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class KafkaReporterConfiguration implements ReporterConfiguration {

  private String bootstrapServers;
  private String topic;
  private String acks;
  private List<Map<String, String>> additionalProperties;

  public String getBootstrapServers() {
    return bootstrapServers;
  }

  public void setBootstrapServers(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public String getAcks() {
    return acks;
  }

  public void setAcks(String acks) {
    this.acks = acks;
  }

  public List<Map<String, String>> getAdditionalProperties() {
    return additionalProperties;
  }

  public void setAdditionalProperties(List<Map<String, String>> additionalProperties) {
    this.additionalProperties = additionalProperties;
  }
}
