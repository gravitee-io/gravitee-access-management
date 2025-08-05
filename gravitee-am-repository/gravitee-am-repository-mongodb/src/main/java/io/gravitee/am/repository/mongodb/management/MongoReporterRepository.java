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

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.ReporterMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoReporterRepository extends AbstractManagementMongoRepository implements ReporterRepository {

    private static final String FIELD_INHERITED = "inherited";
    private MongoCollection<ReporterMongo> reportersCollection;

    @PostConstruct
    public void init() {
        reportersCollection = mongoOperations.getCollection("reporters", ReporterMongo.class);
        super.init(reportersCollection);
        super.createIndex(reportersCollection, Map.of(new Document(FIELD_DOMAIN, 1), new IndexOptions().name("d1")));
    }

    @Override
    public Flowable<Reporter> findAll() {
        return Flowable.fromPublisher(withMaxTime(reportersCollection.find())).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Reporter> findByReference(Reference reference) {
        var query = referenceMatches(reference);
        return Flowable.fromPublisher(reportersCollection.find(query)).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    private static Bson referenceMatches(Reference reference) {
        var query = and(eq(FIELD_REFERENCE_TYPE, reference.type()), eq(FIELD_REFERENCE_ID, reference.id()));
        // for backwards compatibility
        if (reference.type() == ReferenceType.DOMAIN) {
            query = or(query, eq(FIELD_DOMAIN, reference.id()));
        }
        return query;
    }

    @Override
    public Flowable<Reporter> findInheritedFrom(Reference parentReference) {
        var query = and(referenceMatches(parentReference), eq(FIELD_INHERITED, true));
        return Flowable.fromPublisher(reportersCollection.find(query)).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Reporter> findById(String id) {
        return Observable.fromPublisher(reportersCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Reporter> create(Reporter item) {
        ReporterMongo reporter = convert(item);
        reporter.setId(reporter.getId() == null ? RandomString.generate() : reporter.getId());
        return Single.fromPublisher(reportersCollection.insertOne(reporter)).flatMap(success -> {
            item.setId(reporter.getId());
            return Single.just(item);
        })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Reporter> update(Reporter item) {
        ReporterMongo reporter = convert(item);
        return Single.fromPublisher(reportersCollection.replaceOne(eq(FIELD_ID, reporter.getId()), reporter)).flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(reportersCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private ReporterMongo convert(Reporter reporter) {
        if (reporter == null) {
            return null;
        }

        ReporterMongo reporterMongo = new ReporterMongo();
        reporterMongo.setId(reporter.getId());
        reporterMongo.setEnabled(reporter.isEnabled());
        reporterMongo.setReferenceType(reporter.getReference().type());
        reporterMongo.setReferenceId(reporter.getReference().id());
        reporterMongo.setName(reporter.getName());
        reporterMongo.setSystem(reporter.isSystem());
        reporterMongo.setType(reporter.getType());
        reporterMongo.setDataType(reporter.getDataType());
        reporterMongo.setConfiguration(reporter.getConfiguration());
        reporterMongo.setCreatedAt(reporter.getCreatedAt());
        reporterMongo.setUpdatedAt(reporter.getUpdatedAt());
        reporterMongo.setInherited(reporter.isInherited());
        return reporterMongo;
    }

    private Reporter convert(ReporterMongo reporterMongo) {
        if (reporterMongo == null) {
            return null;
        }

        Reporter reporter = new Reporter();
        reporter.setId(reporterMongo.getId());
        reporter.setEnabled(reporterMongo.isEnabled());
        if (reporterMongo.getReferenceType() != null) {
            reporter.setReference(new Reference(reporterMongo.getReferenceType(), reporterMongo.getReferenceId()));
        } else if (StringUtils.hasText(reporterMongo.getDomain())) {
            reporter.setReference(Reference.domain(reporterMongo.getDomain()));
        } else {
            reporter.setReference(Reference.organization(Organization.DEFAULT));
        }
        reporter.setName(reporterMongo.getName());
        reporter.setSystem(reporterMongo.isSystem());
        reporter.setType(reporterMongo.getType());
        reporter.setDataType(reporterMongo.getDataType());
        reporter.setConfiguration(reporterMongo.getConfiguration());
        reporter.setCreatedAt(reporterMongo.getCreatedAt());
        reporter.setUpdatedAt(reporterMongo.getUpdatedAt());
        reporter.setInherited(reporterMongo.isInherited());
        return reporter;
    }
}
