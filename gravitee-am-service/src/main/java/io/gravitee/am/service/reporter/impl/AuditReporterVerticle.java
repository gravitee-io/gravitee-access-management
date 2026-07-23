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

import io.gravitee.am.model.Reference;
import io.gravitee.am.reporter.api.Reportable;
import io.gravitee.am.service.reporter.AuditReporterService;
import io.gravitee.node.reporter.vertx.eventbus.ReportableMessageCodec;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.rxjava3.core.AbstractVerticle;
import org.slf4j.Logger;
import io.gravitee.node.logging.NodeLoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AuditReporterVerticle extends AbstractVerticle implements AuditReporterService {

    public static final Logger LOGGER = NodeLoggerFactory.getLogger(AuditReporterVerticle.class);
    public static final String EVENT_BUS_ADDRESS = "node:audits";
    public static final String EVENT_BUS_ADDRESS_PREFIX = "node:audits.";

    private DeliveryOptions deliveryOptions;

    @Override
    public void start() throws Exception {
        deliveryOptions = new DeliveryOptions().setCodecName(ReportableMessageCodec.CODEC_NAME);
        vertx.eventBus().consumer(EVENT_BUS_ADDRESS, new ReportableHandlerLogger<>());
    }

    @Override
    public void stop() throws Exception {
        // nothing to close
    }

    public void report(Reportable reportable) {
        if (deliveryOptions != null) {
            String address = (reportable.getReferenceType() != null && reportable.getReferenceId() != null)
                    ? addressFor(new Reference(reportable.getReferenceType(), reportable.getReferenceId()))
                    : EVENT_BUS_ADDRESS;
            vertx.eventBus().publish(address, reportable, deliveryOptions);
        }
    }

    public static String addressFor(Reference reference) {
        return EVENT_BUS_ADDRESS_PREFIX + reference.type().name() + "." + reference.id();
    }
}
