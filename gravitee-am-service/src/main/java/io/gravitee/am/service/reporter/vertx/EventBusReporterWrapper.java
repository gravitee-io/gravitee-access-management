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

import io.gravitee.am.model.common.Page;
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

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EventBusReporterWrapper implements Reporter, Handler<Message<Reportable>> {

    public static final Logger logger = LoggerFactory.getLogger(EventBusReporterWrapper.class);
    public static final String EVENT_BUS_ADDRESS = "node:audits";
    private Vertx vertx;
    private String domain;
    private Reporter reporter;
    private MessageConsumer messageConsumer;

    public EventBusReporterWrapper(Vertx vertx, String domain, Reporter reporter) {
        this.vertx = vertx;
        this.domain = domain;
        this.reporter = reporter;
    }

    @Override
    public void handle(Message<Reportable> reportableMsg) {
        Reportable reportable = reportableMsg.body();
        if (domain.equals(reportable.domain()) && reporter.canHandle(reportable)) {
            reporter.report(reportable);
        }
    }

    @Override
    public Single<Page> search(ReportableCriteria criteria, int page, int size) {
        return reporter.search(criteria, page, size);
    }

    @Override
    public Maybe findById(String id) {
        return reporter.findById(id);
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
}
