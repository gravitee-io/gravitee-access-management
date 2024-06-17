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
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.Reportable;
import io.gravitee.am.reporter.api.provider.ReportableCriteria;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.gravitee.common.component.Lifecycle;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.eventbus.Message;
import io.vertx.rxjava3.core.eventbus.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.gravitee.am.service.reporter.impl.AuditReporterVerticle.EVENT_BUS_ADDRESS;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EventBusReporterWrapper implements Reporter, Handler<Message<Reportable>> {

    public static final Logger logger = LoggerFactory.getLogger(EventBusReporterWrapper.class);
    private Vertx vertx;
    private ReferenceType referenceType;
    private String referenceId;
    private Reporter reporter;
    private MessageConsumer messageConsumer;

    public EventBusReporterWrapper(Vertx vertx, Reporter reporter) {
        this(vertx, new Reference(ReferenceType.PLATFORM, Platform.DEFAULT), reporter);
    }

    public EventBusReporterWrapper(Vertx vertx, Reference reference, Reporter reporter) {
        this.vertx = vertx;
        this.referenceType = reference.type();
        this.referenceId = reference.id();
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
        if (referenceType == ReferenceType.PLATFORM) {
            return true;
        }
        return reportable.getReferenceType() == referenceType
                && referenceId.equals(reportable.getReferenceId())
                && reporter.canHandle(reportable);
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
    public Reporter start() throws Exception {
        // start the delegate reporter
        vertx.rxExecuteBlocking(event -> {
                    try {
                        reporter.start();
                        event.complete(reporter);
                    } catch (Exception ex) {
                        logger.error("Error while starting reporter", ex);
                        event.fail(ex);
                    }
                })
                .doOnSuccess(o -> messageConsumer = vertx.eventBus().consumer(EVENT_BUS_ADDRESS, EventBusReporterWrapper.this))
                .subscribe();

        return reporter;
    }

    @Override
    public Reporter stop() throws Exception {
        if (messageConsumer != null) {
            messageConsumer.unregister();
        }
        return (Reporter) reporter.stop();
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
