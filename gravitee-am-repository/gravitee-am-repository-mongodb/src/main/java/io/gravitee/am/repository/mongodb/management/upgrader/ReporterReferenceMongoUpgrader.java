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
package io.gravitee.am.repository.mongodb.management.upgrader;

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.mongodb.management.internal.model.ReporterMongo;
import io.gravitee.node.api.upgrader.Upgrader;
import io.reactivex.rxjava3.core.Flowable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;

@RequiredArgsConstructor
@Slf4j
@Component
public class ReporterReferenceMongoUpgrader implements Upgrader {
    private final MongoDatabase mongo;

    @Override
    public boolean upgrade() {
        var reportersCollection = mongo.getCollection("reporters", ReporterMongo.class);
        return Flowable.fromPublisher(reportersCollection.find(exists("domain")))
                .filter(r -> StringUtils.hasLength(r.getDomain()))
                .flatMap(reporter -> {
                    reporter.setReferenceType(ReferenceType.DOMAIN);
                    reporter.setReferenceId(reporter.getDomain());
                    return reportersCollection.replaceOne(eq("_id", reporter.getId()), reporter);
                })
                .map(r -> r.getModifiedCount() > 0)
                .switchIfEmpty(Flowable.just(true))
                .reduce(Boolean.TRUE, (acc, opResult) -> acc && opResult)
                .onErrorReturn(ex -> {
                    log.error("Error migrating reporters from domainId to reference", ex);
                    return false;
                })
                .blockingGet();
    }

    @Override
    public int getOrder() {
        return -100; // run before other upgraders
    }
}
