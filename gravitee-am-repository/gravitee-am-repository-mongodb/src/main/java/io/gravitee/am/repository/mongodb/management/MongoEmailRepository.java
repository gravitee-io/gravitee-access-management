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

import static com.mongodb.client.model.Filters.*;

import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.EmailRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.EmailMongo;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoEmailRepository extends AbstractManagementMongoRepository implements EmailRepository {

    private static final String FIELD_ID = "_id";
    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_CLIENT = "client";
    private static final String FIELD_TEMPLATE = "template";
    private MongoCollection<EmailMongo> emailsCollection;

    @PostConstruct
    public void init() {
        emailsCollection = mongoOperations.getCollection("emails", EmailMongo.class);
        super.createIndex(emailsCollection, new Document(FIELD_ID, 1));
        super.createIndex(emailsCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1));
        super.createIndex(emailsCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_TEMPLATE, 1));
        super.createIndex(
            emailsCollection,
            new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_CLIENT, 1).append(FIELD_TEMPLATE, 1)
        );
    }

    @Override
    public Single<List<Email>> findAll() {
        return Observable.fromPublisher(emailsCollection.find()).map(this::convert).collect(ArrayList::new, List::add);
    }

    @Override
    public Single<List<Email>> findAll(ReferenceType referenceType, String referenceId) {
        return Observable
            .fromPublisher(emailsCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId))))
            .map(this::convert)
            .collect(ArrayList::new, List::add);
    }

    @Override
    public Single<List<Email>> findByDomain(String domain) {
        return Observable
            .fromPublisher(emailsCollection.find(eq(FIELD_DOMAIN, domain)))
            .map(this::convert)
            .collect(ArrayList::new, List::add);
    }

    @Override
    public Single<List<Email>> findByClient(ReferenceType referenceType, String referenceId, String client) {
        return Observable
            .fromPublisher(
                emailsCollection.find(
                    and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_CLIENT, client))
                )
            )
            .map(this::convert)
            .collect(ArrayList::new, List::add);
    }

    @Override
    public Maybe<Email> findByTemplate(ReferenceType referenceType, String referenceId, String template) {
        return Observable
            .fromPublisher(
                emailsCollection
                    .find(
                        and(
                            eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                            eq(FIELD_REFERENCE_ID, referenceId),
                            eq(FIELD_TEMPLATE, template),
                            exists(FIELD_CLIENT, false)
                        )
                    )
                    .first()
            )
            .firstElement()
            .map(this::convert);
    }

    @Override
    public Maybe<Email> findByDomainAndTemplate(String domain, String template) {
        return findByTemplate(ReferenceType.DOMAIN, domain, template);
    }

    @Override
    public Maybe<Email> findByClientAndTemplate(ReferenceType referenceType, String referenceId, String client, String template) {
        return Observable
            .fromPublisher(
                emailsCollection
                    .find(
                        and(
                            eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                            eq(FIELD_REFERENCE_ID, referenceId),
                            eq(FIELD_CLIENT, client),
                            eq(FIELD_TEMPLATE, template)
                        )
                    )
                    .first()
            )
            .firstElement()
            .map(this::convert);
    }

    @Override
    public Maybe<Email> findByDomainAndClientAndTemplate(String domain, String client, String template) {
        return findByClientAndTemplate(ReferenceType.DOMAIN, domain, client, template);
    }

    @Override
    public Maybe<Email> findById(ReferenceType referenceType, String referenceId, String id) {
        return Observable
            .fromPublisher(
                emailsCollection
                    .find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_ID, id)))
                    .first()
            )
            .firstElement()
            .map(this::convert);
    }

    @Override
    public Maybe<Email> findById(String id) {
        return Observable.fromPublisher(emailsCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<Email> create(Email item) {
        EmailMongo email = convert(item);
        email.setId(email.getId() == null ? RandomString.generate() : email.getId());
        return Single.fromPublisher(emailsCollection.insertOne(email)).flatMap(success -> findById(email.getId()).toSingle());
    }

    @Override
    public Single<Email> update(Email item) {
        EmailMongo email = convert(item);
        return Single
            .fromPublisher(emailsCollection.replaceOne(eq(FIELD_ID, email.getId()), email))
            .flatMap(updateResult -> findById(email.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(emailsCollection.deleteOne(eq(FIELD_ID, id)));
    }

    private Email convert(EmailMongo emailMongo) {
        if (emailMongo == null) {
            return null;
        }
        Email email = new Email();
        email.setId(emailMongo.getId());
        email.setEnabled(emailMongo.isEnabled());
        email.setReferenceType(emailMongo.getReferenceType());
        email.setReferenceId(emailMongo.getReferenceId());
        email.setClient(emailMongo.getClient());
        email.setTemplate(emailMongo.getTemplate());
        email.setFrom(emailMongo.getFrom());
        email.setFromName(emailMongo.getFromName());
        email.setSubject(emailMongo.getSubject());
        email.setContent(emailMongo.getContent());
        email.setExpiresAfter(emailMongo.getExpiresAfter());
        email.setCreatedAt(emailMongo.getCreatedAt());
        email.setUpdatedAt(emailMongo.getUpdatedAt());
        return email;
    }

    private EmailMongo convert(Email email) {
        if (email == null) {
            return null;
        }

        EmailMongo emailMongo = new EmailMongo();
        emailMongo.setId(email.getId());
        emailMongo.setEnabled(email.isEnabled());
        emailMongo.setReferenceType(email.getReferenceType());
        emailMongo.setReferenceId(email.getReferenceId());
        emailMongo.setClient(email.getClient());
        emailMongo.setTemplate(email.getTemplate());
        emailMongo.setFrom(email.getFrom());
        emailMongo.setFromName(email.getFromName());
        emailMongo.setSubject(email.getSubject());
        emailMongo.setContent(email.getContent());
        emailMongo.setExpiresAfter(email.getExpiresAfter());
        emailMongo.setCreatedAt(email.getCreatedAt());
        emailMongo.setUpdatedAt(email.getUpdatedAt());
        return emailMongo;
    }
}
