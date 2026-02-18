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
package io.gravitee.am.repository.mongodb.gateway;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.repository.gateway.api.EmailStagingRepository;
import io.gravitee.am.repository.mongodb.gateway.internal.model.EmailStagingMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoEmailStagingRepository extends AbstractGatewayMongoRepository implements EmailStagingRepository {

    private static final String COLLECTION_NAME = "dp_email_staging";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_REFERENCE_TYPE = "referenceType";
    private static final String FIELD_REFERENCE_ID = "referenceId";
    private static final String FIELD_EMAIL_TEMPLATE_NAME = "emailTemplateName";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    private MongoCollection<EmailStagingMongo> emailStagingCollection;

    @PostConstruct
    public void init() {
        emailStagingCollection = mongoOperations.getCollection(COLLECTION_NAME, EmailStagingMongo.class);
        super.init(emailStagingCollection);

        // Create indexes for efficient queries
        var indexes = Map.of(
            // Index on updatedAt for sorting oldest entries
            new Document(FIELD_REFERENCE_TYPE, 1)
                    .append(FIELD_REFERENCE_ID, 1)
                    .append(FIELD_UPDATED_AT, 1),
            new IndexOptions().name("updated_at_idx")
        );

        super.createIndex(emailStagingCollection, indexes, getEnsureIndexOnStart());
    }

    @Override
    public Single<EmailStaging> create(EmailStaging emailStaging) {
        EmailStagingMongo emailStagingMongo = convert(emailStaging);
        emailStagingMongo.setId(emailStagingMongo.getId() == null ? RandomString.generate() : emailStagingMongo.getId());

        Date now = new Date();
        emailStagingMongo.setCreatedAt(now);
        emailStagingMongo.setUpdatedAt(now);

        return Single.fromPublisher(emailStagingCollection.insertOne(emailStagingMongo))
                .flatMap(success -> Single.just(convert(emailStagingMongo)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(emailStagingCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Completable.complete();
        }

        return Completable.fromPublisher(emailStagingCollection.deleteMany(in(FIELD_ID, ids)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<EmailStaging> findOldestByUpdateDate(Reference reference, int limit) {
        return Observable.fromPublisher(
                withMaxTime(emailStagingCollection.find(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id())))
                        .sort(new Document(FIELD_UPDATED_AT, 1))
                        .limit(limit))
        )
        .map(this::convert)
        .toFlowable(io.reactivex.rxjava3.core.BackpressureStrategy.BUFFER)
        .observeOn(Schedulers.computation());
    }

    @Override
    public Single<EmailStaging> updateAttempts(String id, int attempts) {
        return Observable.fromPublisher(emailStagingCollection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .toSingle()
                .flatMap(emailStagingMongo -> {
                    emailStagingMongo.setAttempts(attempts);
                    emailStagingMongo.setUpdatedAt(new Date());

                    return Observable.fromPublisher(
                            emailStagingCollection.replaceOne(eq(FIELD_ID, id), emailStagingMongo)
                    )
                    .firstElement()
                    .toSingle()
                    .map(result -> convert(emailStagingMongo));
                })
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
