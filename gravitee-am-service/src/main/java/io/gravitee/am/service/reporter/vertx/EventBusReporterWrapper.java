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
import io.gravitee.am.common.event.Action;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.reporter.api.Reportable;
import io.gravitee.am.reporter.api.provider.ReportableCriteria;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.gravitee.common.component.Lifecycle;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.eventbus.Message;
import io.vertx.rxjava3.core.eventbus.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static io.gravitee.am.service.reporter.impl.AuditReporterVerticle.EVENT_BUS_ADDRESS;
import static io.gravitee.am.service.reporter.impl.AuditReporterVerticle.addressFor;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EventBusReporterWrapper<R extends Reportable,C extends ReportableCriteria> implements Reporter<R, C>, Handler<Message<Reportable>> {

    public static final Logger logger = LoggerFactory.getLogger(EventBusReporterWrapper.class);
    private final Vertx vertx;
    private final Reporter<R,C> reporter;
    private final List<MessageConsumer<Reportable>> messageConsumers = Collections.synchronizedList(new ArrayList<>());
    private Set<Reference> referenceFilter;


    public EventBusReporterWrapper(Vertx vertx, Reporter<R,C> reporter) {
        this.vertx = vertx;
        this.reporter = reporter;
        this.referenceFilter = null;
    }


    public EventBusReporterWrapper(Vertx vertx,  Reporter<R,C> reporter, Reference reference) {
        this(vertx, reporter, Set.of(reference));
    }

    public EventBusReporterWrapper(Vertx vertx,  Reporter<R,C> reporter, Collection<Reference> references) {
        Objects.requireNonNull(references, "references");
        this.vertx = vertx;
        this.referenceFilter = new ConcurrentHashSet<>();
        this.referenceFilter.addAll(references);
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

    boolean canHandle(Reportable reportable) {
        return (referenceFilter == null || referenceFilter.contains(reportable.getReference()))
                && reporter.canHandle(reportable);
    }


    @Override
    public Single<Page<R>> search(ReferenceType referenceType, String referenceId, C criteria, int page, int size) {
        return reporter.search(referenceType, referenceId, criteria, page, size);
    }

    @Override
    public Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId, C criteria, Type analyticsType) {
        return reporter.aggregate(referenceType, referenceId, criteria, analyticsType);
    }

    @Override
    public Maybe<R> findById(ReferenceType referenceType, String referenceId, String id) {
        return reporter.findById(referenceType, referenceId, id);
    }

    @Override
    public Completable purgeExpiredData() {
        logger.debug("Delegating purge to underlying reporter: {}", reporter.getClass().getSimpleName());
        return reporter.purgeExpiredData();
    }

    @Override
    public Completable purgeExpiredData(java.time.Instant deadline) {
        logger.debug("Delegating purge to underlying reporter: {}", reporter.getClass().getSimpleName());
        return reporter.purgeExpiredData(deadline);
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
    public Reporter<R,C> start() throws Exception {
        vertx.rxExecuteBlocking(event -> {
                    try {
                        event.complete(reporter);
                    } catch (Exception ex) {
                        logger.error("Error while starting reporter", ex);
                        event.fail(ex);
                    }
                })
                .doOnSuccess(o -> {
                    if (referenceFilter == null) {
                        messageConsumers.add(vertx.eventBus().consumer(EVENT_BUS_ADDRESS, EventBusReporterWrapper.this));
                    } else {
                        for (Reference ref : referenceFilter) {
                            messageConsumers.add(vertx.eventBus().consumer(addressFor(ref), EventBusReporterWrapper.this));
                        }
                    }
                })
                .doOnError(ex -> logger.error("Error while starting reporter", ex))
                .subscribe();

        return reporter;
    }

    @Override
    public Reporter<R,C> stop() throws Exception {
        unregister();
        return (Reporter<R, C>) reporter.stop();
    }

    public void unregister() {
        messageConsumers.forEach(MessageConsumer::unregister);
        messageConsumers.clear();
    }

    public void updateReferences(ChildReporterAction referenceChange) {
        switch (referenceChange.op()) {
            case CREATE -> {
                referenceFilter.add(referenceChange.reference());
                messageConsumers.add(vertx.eventBus().consumer(addressFor(referenceChange.reference()), EventBusReporterWrapper.this));
            }
            case DELETE -> {
                referenceFilter.remove(referenceChange.reference());
                String removedAddress = addressFor(referenceChange.reference());
                messageConsumers.removeIf(c -> {
                    if (removedAddress.equals(c.address())) {
                        c.unregister();
                        return true;
                    }
                    return false;
                });
            }
            default -> logger.debug("Ignoring {}", referenceChange);
        }
        logger.info("Reporter {}: updated reference list to {}", reporter, referenceFilter);
    }

    public record ChildReporterAction(Action op, Reference reference) {
        public static ChildReporterAction of(Payload content) {
            return getAction(content)
                    .flatMap(action -> getChildReference(content)
                            .map(ref -> new ChildReporterAction(action, ref)))
                    .orElse(null);
        }


        private static Optional<Action> getAction(Payload payload) {
            return Optional.ofNullable(payload.get("childReporterAction"))
                    .filter(String.class::isInstance)
                    .map(a -> Action.valueOf((String) a));
        }


        private static Optional<Reference> getChildReference(Payload payload) {
            return Optional.ofNullable(payload.get("childReporterReference"))
                    .filter(Map.class::isInstance)
                    .map(ref -> (Map<String, String> ) ref)
                    .map(refMap -> new Reference(ReferenceType.valueOf(refMap.get("type")), refMap.get("id")));
        }
    }
}
