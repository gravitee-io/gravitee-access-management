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
package io.gravitee.am.service.reporter.impl;

import io.gravitee.am.reporter.api.Reportable;
import io.gravitee.am.service.reporter.AuditReporterService;
import io.gravitee.am.service.reporter.vertx.EventBusReporterWrapper;
import io.gravitee.node.reporter.vertx.eventbus.ReportableMessageCodec;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.core.eventbus.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AuditReporterVerticle extends AbstractVerticle implements AuditReporterService {

    public static final Logger LOGGER = LoggerFactory.getLogger(AuditReporterVerticle.class);
    public static final String EVENT_BUS_ADDRESS = "node:audits";
    private MessageProducer<Reportable> producer;

    @Override
    public void start() throws Exception {
        producer = vertx.eventBus()
                .<Reportable>publisher(
                        EVENT_BUS_ADDRESS,
                        new DeliveryOptions()
                                .setCodecName(ReportableMessageCodec.CODEC_NAME));

        vertx.eventBus().consumer(EVENT_BUS_ADDRESS, new ReportableHandlerLogger<>());
    }

    @Override
    public void stop() throws Exception {
        if (producer != null) {
            producer.close();
        }
    }

    public void report(Reportable reportable) {
        if (producer != null) {
            producer.write(reportable)
                    .doOnError(throwable -> LOGGER.error("Unexpected error while sending a reportable element", throwable))
                    .subscribe();
        }
    }
}
