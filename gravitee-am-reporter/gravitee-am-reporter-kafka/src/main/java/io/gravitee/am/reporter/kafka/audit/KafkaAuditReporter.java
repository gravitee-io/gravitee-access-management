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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.kafka.KafkaReporterConfiguration;
import io.gravitee.am.reporter.kafka.dto.AuditMessageValueDto;
import io.gravitee.am.reporter.kafka.kafka.JacksonSerializer;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.Node;
import io.gravitee.reporter.api.Reportable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Florent Amaridon
 * @author Visiativ
 */
public class KafkaAuditReporter extends AbstractService implements AuditReporter, InitializingBean {

  private static Logger LOGGER = LoggerFactory.getLogger(KafkaAuditReporter.class);

  @Autowired
  private Vertx vertx;

  @Autowired
  private KafkaReporterConfiguration config;

  @Autowired
  private GraviteeContext context;

  @Autowired
  private Node node;

  private final DtoMapper dtoMapper;

  private KafkaProducer<String, AuditMessageValueDto> producer;

  public KafkaAuditReporter() {
    this.dtoMapper = new DtoMapper();
  }

  @Override
  public boolean canHandle(Reportable reportable) {
    return reportable instanceof Audit;
  }

  @Override
  public void report(Reportable reportable) {
    LOGGER.debug("Report({})", reportable);
    if (producer != null) {
      AuditMessageValueDto value = dtoMapper.map((Audit) reportable, this.context, this.node);
      this.producer.write(
          KafkaProducerRecord.create(this.config.getTopic(), this.context.getDomainId(), value));
    } else {
      LOGGER.debug("Producer is null, ignore reportable");
    }
  }

  @Override
  public void afterPropertiesSet() {
    if (context == null) {
      context = GraviteeContext.defaultContext(null);
    }
  }

  @Override
  protected void doStart() {

    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", this.config.getBootstrapServers());
    config.put("acks", this.config.getAcks());

    List<Map<String, String>> additionalProperties = this.config.getAdditionalProperties();
    if (additionalProperties != null && !additionalProperties.isEmpty()) {
      additionalProperties.forEach(claimMapper -> {
        String option = claimMapper.get("option");
        String value = claimMapper.get("value");
        config.put(option, value);
      });
    }

    Serializer<String> keySerializer = new StringSerializer();
    Serializer<AuditMessageValueDto> valueSerializer = new JacksonSerializer<>();

    this.producer = KafkaProducer.create(vertx, config, keySerializer, valueSerializer);
  }

  @Override
  protected void doStop() throws Exception {
    super.doStop();
    if (this.producer != null) {
      this.producer.close();
    }
  }

  @Override
  public boolean canSearch() {
    return false;
  }

  @Override
  public Single<Page<Audit>> search(ReferenceType referenceType, String referenceId,
      AuditReportableCriteria criteria, int page, int size) {
    throw new IllegalStateException("Search method not implemented for File reporter");
  }

  @Override
  public Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId,
      AuditReportableCriteria criteria, Type analyticsType) {
    throw new IllegalStateException("Aggregate method not implemented for File reporter");
  }

  @Override
  public Maybe<Audit> findById(ReferenceType referenceType, String referenceId, String id) {
    throw new IllegalStateException("FindById method not implemented for File reporter");
  }
}
