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
package io.gravitee.am.repository.mongodb.management;

import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.repository.mongodb.common.LoggableIndexSubscriber;
import io.gravitee.am.repository.mongodb.management.internal.model.ReporterMongo;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoReporterRepository extends AbstractManagementMongoRepository implements ReporterRepository {

    private static final String FIELD_ID = "_id";
    private static final String FIELD_DOMAIN = "domain";
    private MongoCollection<ReporterMongo> reportersCollection;

    @PostConstruct
    public void init() {
        reportersCollection = mongoOperations.getCollection("reporters", ReporterMongo.class);
        reportersCollection.createIndex(new Document(FIELD_DOMAIN, 1)).subscribe(new LoggableIndexSubscriber());
    }

    @Override
    public Single<List<Reporter>> findAll() {
        return Observable.fromPublisher(reportersCollection.find()).map(this::convert).collect(ArrayList::new, List::add);
    }

    @Override
    public Single<List<Reporter>> findByDomain(String domain) {
        return Observable.fromPublisher(reportersCollection.find(eq(FIELD_DOMAIN, domain))).map(this::convert).collect(ArrayList::new, List::add);
    }

    @Override
    public Maybe<Reporter> findById(String id) {
        return Observable.fromPublisher(reportersCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<Reporter> create(Reporter item) {
        ReporterMongo reporter = convert(item);
        reporter.setId(reporter.getId() == null ? RandomString.generate() : reporter.getId());
        return Single.fromPublisher(reportersCollection.insertOne(reporter)).flatMap(success -> findById(reporter.getId()).toSingle());
    }

    @Override
    public Single<Reporter> update(Reporter item) {
        ReporterMongo reporter = convert(item);
        return Single.fromPublisher(reportersCollection.replaceOne(eq(FIELD_ID, reporter.getId()), reporter)).flatMap(updateResult -> findById(reporter.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(reportersCollection.deleteOne(eq(FIELD_ID, id)));
    }

    private ReporterMongo convert(Reporter reporter) {
        if (reporter == null) {
            return null;
        }

        ReporterMongo reporterMongo = new ReporterMongo();
        reporterMongo.setId(reporter.getId());
        reporterMongo.setEnabled(reporter.isEnabled());
        reporterMongo.setDomain(reporter.getDomain());
        reporterMongo.setName(reporter.getName());
        reporterMongo.setType(reporter.getType());
        reporterMongo.setDataType(reporter.getDataType());
        reporterMongo.setConfiguration(reporter.getConfiguration());
        reporterMongo.setCreatedAt(reporter.getCreatedAt());
        reporterMongo.setUpdatedAt(reporter.getUpdatedAt());
        return reporterMongo;
    }

    private Reporter convert(ReporterMongo reporterMongo) {
        if (reporterMongo == null) {
            return null;
        }

        Reporter reporter = new Reporter();
        reporter.setId(reporterMongo.getId());
        reporter.setEnabled(reporterMongo.isEnabled());
        reporter.setDomain(reporterMongo.getDomain());
        reporter.setName(reporterMongo.getName());
        reporter.setType(reporterMongo.getType());
        reporter.setDataType(reporterMongo.getDataType());
        reporter.setConfiguration(reporterMongo.getConfiguration());
        reporter.setCreatedAt(reporterMongo.getCreatedAt());
        reporter.setUpdatedAt(reporterMongo.getUpdatedAt());
        return reporter;
    }
}
