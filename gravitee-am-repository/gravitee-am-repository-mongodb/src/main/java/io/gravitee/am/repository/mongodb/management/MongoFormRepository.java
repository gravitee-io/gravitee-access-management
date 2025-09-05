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
import io.gravitee.am.model.Form;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.FormRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.FormMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoFormRepository extends AbstractManagementMongoRepository implements FormRepository {

    private static final String FIELD_TEMPLATE = "template";
    private MongoCollection<FormMongo> formsCollection;

    @PostConstruct
    public void init() {
        formsCollection = mongoOperations.getCollection("forms", FormMongo.class);
        super.init(formsCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1), new IndexOptions().name("rt1ri1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_TEMPLATE, 1), new IndexOptions().name("rt1ri1t1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_CLIENT, 1).append(FIELD_TEMPLATE, 1), new IndexOptions().name("rt1ri1c1t1"));

        super.createIndex(formsCollection, indexes);
    }

    @Override
    public Flowable<Form> findAll(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(withMaxTime(formsCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId))))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Form> findAll(ReferenceType referenceType) {
        return Flowable.fromPublisher(withMaxTime(formsCollection.find(eq(FIELD_REFERENCE_TYPE, referenceType.name())))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Form> findByClient(ReferenceType referenceType, String referenceId, String client) {
        return Flowable.fromPublisher(
                formsCollection.find(
                        and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_CLIENT, client))
                )).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Form> findByTemplate(ReferenceType referenceType, String referenceId, String template) {
        return Observable.fromPublisher(
                formsCollection.find(
                        and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_TEMPLATE, template),
                                exists(FIELD_CLIENT, false)))
                        .first())
                .firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Form> findByClientAndTemplate(ReferenceType referenceType, String referenceId, String client, String template) {
        return Observable.fromPublisher(
                formsCollection.find(
                        and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_CLIENT, client),
                                eq(FIELD_TEMPLATE, template)))
                        .first())
                .firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Form> findById(ReferenceType referenceType, String referenceId, String id) {
        return Observable.fromPublisher(formsCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_ID, id))).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Form> findById(String id) {
        return Observable.fromPublisher(formsCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Form> create(Form item) {
        FormMongo page = convert(item);
        page.setId(page.getId() == null ? RandomString.generate() : page.getId());
        return Single.fromPublisher(formsCollection.insertOne(page)).flatMap(success -> { item.setId(page.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Form> update(Form item) {
        FormMongo page = convert(item);
        return Single.fromPublisher(formsCollection.replaceOne(eq(FIELD_ID, page.getId()), page)).flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(formsCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private Form convert(FormMongo pageMongo) {
        if (pageMongo == null) {
            return null;
        }
        Form page = new Form();
        page.setId(pageMongo.getId());
        page.setEnabled(pageMongo.isEnabled());
        page.setReferenceType(ReferenceType.valueOf(pageMongo.getReferenceType()));
        page.setReferenceId(pageMongo.getReferenceId());
        page.setClient(pageMongo.getClient());
        page.setTemplate(pageMongo.getTemplate());
        page.setContent(pageMongo.getContent());
        page.setAssets(pageMongo.getAssets());
        page.setCreatedAt(pageMongo.getCreatedAt());
        page.setUpdatedAt(pageMongo.getUpdatedAt());
        return page;
    }

    private FormMongo convert(Form page) {
        if (page == null) {
            return null;
        }

        FormMongo pageMongo = new FormMongo();
        pageMongo.setId(page.getId());
        pageMongo.setEnabled(page.isEnabled());
        pageMongo.setReferenceType(page.getReferenceType().name());
        pageMongo.setReferenceId(page.getReferenceId());
        pageMongo.setClient(page.getClient());
        pageMongo.setTemplate(page.getTemplate());
        pageMongo.setContent(page.getContent());
        pageMongo.setAssets(page.getAssets());
        pageMongo.setCreatedAt(page.getCreatedAt());
        pageMongo.setUpdatedAt(page.getUpdatedAt());
        return pageMongo;
    }
}
