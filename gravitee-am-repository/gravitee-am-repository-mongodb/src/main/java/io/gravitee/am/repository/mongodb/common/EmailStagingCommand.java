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
package io.gravitee.am.repository.mongodb.common;

import com.mongodb.client.model.Aggregates;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.mongodb.common.model.EmailStagingMongo;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.AllArgsConstructor;
import org.bson.Document;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

@AllArgsConstructor
public class EmailStagingCommand {

    private static final String FIELD_ID = "_id";
    private static final String FIELD_REFERENCE_TYPE = "referenceType";
    private static final String FIELD_REFERENCE_ID = "referenceId";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    private final MongoCollection<EmailStagingMongo> collection;

    private final MongoCollection<EmailStagingMongo> readCollection;

    private final int maxTimeInMs;

    public Single<EmailStaging> create(EmailStaging emailStaging) {
        EmailStagingMongo emailStagingMongo = convert(emailStaging);
        emailStagingMongo.setId(emailStagingMongo.getId() == null ? RandomString.generate() : emailStagingMongo.getId());

        Date now = new Date();
        emailStagingMongo.setCreatedAt(now);
        emailStagingMongo.setUpdatedAt(now);

        return Single.fromPublisher(collection.insertOne(emailStagingMongo))
                .flatMap(success -> Single.just(convert(emailStagingMongo)))
                .observeOn(Schedulers.computation());
    }

    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    public Completable delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Completable.complete();
        }

        return Completable.fromPublisher(collection.deleteMany(in(FIELD_ID, ids)))
                .observeOn(Schedulers.computation());
    }

    public Flowable<EmailStaging> findOldestByUpdateDate(Reference reference, int limit) {
        return Observable.fromPublisher(
                        collection.find(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id())))
                                .sort(new Document(FIELD_UPDATED_AT, 1))
                                .limit(limit)
                                .maxTime(maxTimeInMs, TimeUnit.MILLISECONDS)
                )
                .map(this::convert)
                .toFlowable(BackpressureStrategy.BUFFER)
                .observeOn(Schedulers.computation());
    }

    public Single<EmailStaging> updateAttempts(String id, int attempts) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .toSingle()
                .flatMap(emailStagingMongo -> {
                    emailStagingMongo.setAttempts(attempts);
                    emailStagingMongo.setUpdatedAt(new Date());

                    return Observable.fromPublisher(collection.replaceOne(eq(FIELD_ID, id), emailStagingMongo))
                            .firstElement()
                            .toSingle()
                            .map(result -> convert(emailStagingMongo));
                })
                .observeOn(Schedulers.computation());
    }

    public Flowable<Reference> listReferences() {
        return Observable.fromPublisher(
                        readCollection.aggregate(
                                List.of(
                                        Aggregates.group(new Document(FIELD_REFERENCE_TYPE, "$" + FIELD_REFERENCE_TYPE)
                                                .append(FIELD_REFERENCE_ID, "$" + FIELD_REFERENCE_ID))
                                ), Document.class
                        ).maxTime(maxTimeInMs, TimeUnit.MILLISECONDS)
                )
                .map(doc -> {
                    Document id = doc.get(FIELD_ID, Document.class);
                    return new Reference(ReferenceType.valueOf(id.getString(FIELD_REFERENCE_TYPE)), id.getString(FIELD_REFERENCE_ID));
                })
                .toFlowable(BackpressureStrategy.BUFFER)
                .observeOn(Schedulers.computation());
    }

    private EmailStaging convert(EmailStagingMongo emailStagingMongo) {
        if (emailStagingMongo == null) {
            return null;
        }

        EmailStaging emailStaging = new EmailStaging();
        emailStaging.setId(emailStagingMongo.getId());
        emailStaging.setUserId(emailStagingMongo.getUserId());
        emailStaging.setApplicationId(emailStagingMongo.getApplicationId());
        emailStaging.setReferenceType(emailStagingMongo.getReferenceType());
        emailStaging.setReferenceId(emailStagingMongo.getReferenceId());
        emailStaging.setEmailTemplateName(emailStagingMongo.getEmailTemplateName());
        emailStaging.setAttempts(emailStagingMongo.getAttempts());
        emailStaging.setCreatedAt(emailStagingMongo.getCreatedAt());
        emailStaging.setUpdatedAt(emailStagingMongo.getUpdatedAt());
        return emailStaging;
    }

    private EmailStagingMongo convert(EmailStaging emailStaging) {
        if (emailStaging == null) {
            return null;
        }

        EmailStagingMongo emailStagingMongo = new EmailStagingMongo();
        emailStagingMongo.setId(emailStaging.getId());
        emailStagingMongo.setUserId(emailStaging.getUserId());
        emailStagingMongo.setApplicationId(emailStaging.getApplicationId());
        emailStagingMongo.setReferenceType(emailStaging.getReferenceType());
        emailStagingMongo.setReferenceId(emailStaging.getReferenceId());
        emailStagingMongo.setEmailTemplateName(emailStaging.getEmailTemplateName());
        emailStagingMongo.setAttempts(emailStaging.getAttempts());
        emailStagingMongo.setCreatedAt(emailStaging.getCreatedAt());
        emailStagingMongo.setUpdatedAt(emailStaging.getUpdatedAt());
        return emailStagingMongo;
    }
}
