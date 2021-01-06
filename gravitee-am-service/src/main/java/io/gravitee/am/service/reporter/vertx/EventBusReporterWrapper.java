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
package io.gravitee.am.service.reporter.vertx;

import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.Reportable;
import io.gravitee.am.reporter.api.provider.ReportableCriteria;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.gravitee.common.component.Lifecycle;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.core.eventbus.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EventBusReporterWrapper implements Reporter, Handler<Message<Reportable>> {

    public static final Logger logger = LoggerFactory.getLogger(EventBusReporterWrapper.class);
    public static final String EVENT_BUS_ADDRESS = "node:audits";
    private Vertx vertx;
    private ReferenceType referenceType;
    private String referenceId;
    private Reporter reporter;
    private MessageConsumer messageConsumer;

    public EventBusReporterWrapper(Vertx vertx, String domain, Reporter reporter) {
        this(vertx, ReferenceType.DOMAIN, domain, reporter);
    }

    public EventBusReporterWrapper(Vertx vertx, Reporter reporter) {
        this(vertx, ReferenceType.PLATFORM, Platform.DEFAULT, reporter);
    }

    private EventBusReporterWrapper(Vertx vertx, ReferenceType referenceType, String referenceId, Reporter reporter) {
        this.vertx = vertx;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.reporter = reporter;
    }

    @Override
    public boolean canSearch() {
        return this.reporter.canSearch();
    }

    @Override
    public void handle(Message<Reportable> reportableMsg) {
        Reportable reportable = reportableMsg.body();

        if (canHandle(reportable)) {
            reporter.report(reportable);
        }
    }

    private boolean canHandle(Reportable reportable) {

        if (reportable.getReferenceType() == ReferenceType.DOMAIN) {
            return referenceType == ReferenceType.DOMAIN
                    && referenceId.equals(reportable.getReferenceId()) && reporter.canHandle(reportable);
        }

        return referenceType == ReferenceType.PLATFORM;
    }

    @Override
    public Single<Page> search(ReferenceType referenceType, String referenceId, ReportableCriteria criteria, int page, int size) {
        return reporter.search(referenceType, referenceId, criteria, page, size);
    }

    @Override
    public Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId, ReportableCriteria criteria, Type analyticsType) {
        return reporter.aggregate(referenceType, referenceId, criteria, analyticsType);
    }

    @Override
    public Maybe findById(ReferenceType referenceType, String referenceId, String id) {
        return reporter.findById(referenceType, referenceId, id);
    }

    @Override
    public void report(io.gravitee.reporter.api.Reportable reportable) {
        // Done by the event bus handler
        // See handle method
    }

    @Override
    public Lifecycle.State lifecycleState() {
        return reporter.lifecycleState();
    }

    @Override
    public Object start() throws Exception {
        // start the delegate reporter
        vertx.executeBlocking(event -> {
            try {
                reporter.start();
                event.complete(reporter);
            } catch (Exception ex) {
                logger.error("Error while starting reporter", ex);
                event.fail(ex);
            }
        }, event -> {
            if (event.succeeded()) {
                messageConsumer = vertx.eventBus().consumer(EVENT_BUS_ADDRESS, EventBusReporterWrapper.this);
            }
        });

        return reporter;
    }

    @Override
    public Object stop() throws Exception {
        messageConsumer.unregister();
        return reporter.stop();
    }

    public void unregister() {
        messageConsumer.unregister();
    }


    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }
}
