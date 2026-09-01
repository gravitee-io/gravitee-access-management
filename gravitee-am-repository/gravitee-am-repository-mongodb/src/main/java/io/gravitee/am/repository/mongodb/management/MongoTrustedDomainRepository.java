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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.jose.JWKModule;
import io.gravitee.am.model.oidc.CrossAppAccessResourceServerView;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.model.oidc.TrustedDomain;
import io.gravitee.am.repository.management.api.TrustedDomainRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.CrossAppAccessSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.SpiffeTrustSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.TokenExchangeTrustSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.TrustDomainKeyMaterialMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.TrustedDomainMongo;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Aggregates.limit;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Aggregates.project;
import static com.mongodb.client.model.Aggregates.sort;
import static com.mongodb.client.model.Aggregates.unwind;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Filters.regex;
import static com.mongodb.client.model.Projections.computed;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Sorts.ascending;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;

/**
 * @author GraviteeSource Team
 */
@Component
public class MongoTrustedDomainRepository extends AbstractManagementMongoRepository implements TrustedDomainRepository {

    private static final String COLLECTION_NAME = "trusted_domains";

    private static final String FIELD_NAME = "name";
    private static final String FIELD_DOMAIN_IDENTIFIER = "domainIdentifier";
    private static final String FIELD_SPIFFE_TRUST_DOMAIN = "spiffe.spiffeTrustDomain";
    private static final String FIELD_CROSS_APP_ACCESS = "crossAppAccess";
    private static final String FIELD_XAA_ENABLED = FIELD_CROSS_APP_ACCESS + ".enabled";
    private static final String FIELD_RESOURCE_SERVERS = FIELD_CROSS_APP_ACCESS + ".resourceServers";
    private static final String FIELD_RESOURCE_SERVER_ID = FIELD_RESOURCE_SERVERS + "." + FIELD_ID;
    private static final String FIELD_RESOURCE_SERVER_NAME = FIELD_RESOURCE_SERVERS + ".name";
    private static final String FIELD_RESOURCE_SERVER_RESOURCE = FIELD_RESOURCE_SERVERS + ".resource";
    private static final String FIELD_TRUSTED_DOMAIN_NAME = "trustedDomainName";
    private static final String PROJECTED_RESOURCE_SERVER_ID = "resourceServerId";
    private static final String FIELD_RESOURCE = "resource";

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JWKModule());

    private MongoCollection<TrustedDomainMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection(COLLECTION_NAME, TrustedDomainMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(
                new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_NAME, 1),
                new IndexOptions().name("rt1ri1n1").unique(true),
                new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_SPIFFE_TRUST_DOMAIN, 1),
                new IndexOptions().name("rt1ri1std1").unique(true)
                        .partialFilterExpression(exists(FIELD_SPIFFE_TRUST_DOMAIN, true)),
                new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_DOMAIN_IDENTIFIER, 1),
                new IndexOptions().name("rt1ri1di1").unique(true)
                        .partialFilterExpression(exists(FIELD_DOMAIN_IDENTIFIER, true))
        ));
        new LegacyTrustDomainMigration(mongoOperations, COLLECTION_NAME).run().subscribe();
    }

    @Override
    public Maybe<TrustedDomain> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<TrustedDomain> create(TrustedDomain item) {
        TrustedDomainMongo doc = toMongo(item);
        doc.setId(doc.getId() == null ? RandomString.generate() : doc.getId());
        return Single.fromPublisher(collection.insertOne(doc))
                .map(success -> {
                    item.setId(doc.getId());
                    return item;
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<TrustedDomain> update(TrustedDomain item) {
        TrustedDomainMongo doc = toMongo(item);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, doc.getId()), doc))
                .map(updateResult -> item)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<TrustedDomain> findByReference(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(collection.find(and(
                        eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                        eq(FIELD_REFERENCE_ID, referenceId))))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<TrustedDomain> findByName(ReferenceType referenceType, String referenceId, String name) {
        return findByField(referenceType, referenceId, FIELD_NAME, name);
    }

    @Override
    public Maybe<TrustedDomain> findBySpiffeTrustDomain(ReferenceType referenceType, String referenceId, String spiffeTrustDomain) {
        return findByField(referenceType, referenceId, FIELD_SPIFFE_TRUST_DOMAIN, spiffeTrustDomain);
    }

    @Override
    public Maybe<TrustedDomain> findByIssuer(ReferenceType referenceType, String referenceId, String issuer) {
        return findByField(referenceType, referenceId, FIELD_DOMAIN_IDENTIFIER, issuer);
    }

    @Override
    public Flowable<CrossAppAccessResourceServerView> searchCrossAppAccessResourceServers(Reference reference, String query, int limit) {
        List<Bson> pipeline = new ArrayList<>(List.of(
                match(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()),
                        eq(FIELD_REFERENCE_ID, reference.id()),
                        eq(FIELD_XAA_ENABLED, true))),
                unwind("$" + FIELD_RESOURCE_SERVERS)));
        if (query != null && !query.isBlank()) {
            Pattern term = Pattern.compile(Pattern.quote(query.trim()), Pattern.CASE_INSENSITIVE);
            pipeline.add(match(or(regex(FIELD_RESOURCE_SERVER_NAME, term),
                    regex(FIELD_RESOURCE_SERVER_RESOURCE, term),
                    regex(FIELD_NAME, term))));
        }
        pipeline.add(sort(ascending(FIELD_NAME, FIELD_RESOURCE_SERVER_NAME)));
        pipeline.add(limit(limit));
        pipeline.add(project(fields(
                computed(FIELD_TRUSTED_DOMAIN_NAME, "$" + FIELD_NAME),
                computed(PROJECTED_RESOURCE_SERVER_ID, "$" + FIELD_RESOURCE_SERVER_ID),
                computed(FIELD_NAME, "$" + FIELD_RESOURCE_SERVER_NAME),
                computed(FIELD_RESOURCE, "$" + FIELD_RESOURCE_SERVER_RESOURCE))));
        return Flowable.fromPublisher(collection.aggregate(pipeline, Document.class))
                .map(MongoTrustedDomainRepository::toResourceServerView)
                .observeOn(Schedulers.computation());
    }

    private static CrossAppAccessResourceServerView toResourceServerView(Document document) {
        return new CrossAppAccessResourceServerView(
                document.getString(FIELD_ID),
                document.getString(FIELD_TRUSTED_DOMAIN_NAME),
                document.getString(PROJECTED_RESOURCE_SERVER_ID),
                document.getString(FIELD_NAME),
                document.getString(FIELD_RESOURCE));
    }

    private Maybe<TrustedDomain> findByField(ReferenceType referenceType, String referenceId, String field, String value) {
        return Observable.fromPublisher(collection.find(and(
                        eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                        eq(FIELD_REFERENCE_ID, referenceId),
                        eq(field, value))).first())
                .firstElement()
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    private TrustedDomain toEntity(TrustedDomainMongo doc) {
        if (doc == null) {
            return null;
        }
        TrustedDomain td = new TrustedDomain();
        td.setId(doc.getId());
        td.setReferenceId(doc.getReferenceId());
        td.setReferenceType(doc.getReferenceType() != null ? ReferenceType.valueOf(doc.getReferenceType()) : null);
        td.setName(doc.getName());
        td.setDescription(doc.getDescription());
        td.setDomainIdentifier(doc.getDomainIdentifier());
        td.setKeyMaterial(toModel(doc.getKeyMaterial()));
        td.setSpiffe(doc.getSpiffe() != null ? doc.getSpiffe().convert() : null);
        td.setTokenExchange(doc.getTokenExchange() != null ? doc.getTokenExchange().convert() : null);
        td.setCrossAppAccess(doc.getCrossAppAccess() != null ? doc.getCrossAppAccess().convert() : null);
        td.setCreatedAt(doc.getCreatedAt());
        td.setUpdatedAt(doc.getUpdatedAt());
        return td;
    }

    private TrustedDomainMongo toMongo(TrustedDomain td) {
        if (td == null) {
            return null;
        }
        TrustedDomainMongo doc = new TrustedDomainMongo();
        doc.setId(td.getId());
        doc.setReferenceId(td.getReferenceId());
        doc.setReferenceType(td.getReferenceType() != null ? td.getReferenceType().name() : null);
        doc.setName(td.getName());
        doc.setDescription(td.getDescription());
        doc.setDomainIdentifier(td.getDomainIdentifier());
        doc.setKeyMaterial(toMongo(td.getKeyMaterial()));
        doc.setSpiffe(SpiffeTrustSettingsMongo.convert(td.getSpiffe()));
        doc.setTokenExchange(TokenExchangeTrustSettingsMongo.convert(td.getTokenExchange(), td.trustsTokenExchange()));
        doc.setCrossAppAccess(CrossAppAccessSettingsMongo.convert(td.getCrossAppAccess()));
        doc.setCreatedAt(td.getCreatedAt());
        doc.setUpdatedAt(td.getUpdatedAt());
        return doc;
    }

    private static TrustDomainKeyMaterial toModel(TrustDomainKeyMaterialMongo doc) {
        if (doc == null) {
            return null;
        }
        return TrustDomainKeyMaterial.builder()
                .source(doc.getSource() != null ? KeyMaterialSource.valueOf(doc.getSource()) : null)
                .jwksUrl(doc.getJwksUrl())
                .refreshIntervalSeconds(doc.getRefreshIntervalSeconds())
                .jwkSet(parseJwkSet(doc.getJwkSet()))
                .certificate(doc.getCertificate())
                .build();
    }

    private static TrustDomainKeyMaterialMongo toMongo(TrustDomainKeyMaterial keyMaterial) {
        if (keyMaterial == null) {
            return null;
        }
        TrustDomainKeyMaterialMongo doc = new TrustDomainKeyMaterialMongo();
        doc.setSource(keyMaterial.getSource() != null ? keyMaterial.getSource().name() : null);
        doc.setJwksUrl(keyMaterial.getJwksUrl());
        doc.setRefreshIntervalSeconds(keyMaterial.getRefreshIntervalSeconds());
        doc.setJwkSet(serializeJwkSet(keyMaterial.getJwkSet()));
        doc.setCertificate(keyMaterial.getCertificate());
        return doc;
    }

    private static JWKSet parseJwkSet(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, JWKSet.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse trust domain JWK set", e);
        }
    }

    private static String serializeJwkSet(JWKSet jwkSet) {
        if (jwkSet == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(jwkSet);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize trust domain JWK set", e);
        }
    }
}
