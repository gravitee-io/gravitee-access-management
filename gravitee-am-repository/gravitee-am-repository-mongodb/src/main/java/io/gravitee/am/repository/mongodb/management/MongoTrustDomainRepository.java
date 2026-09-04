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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.jose.JWKModule;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.oidc.SpiffeTrustSettings;
import io.gravitee.am.model.oidc.TokenExchangeTrustSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.TrustDomainKeyMaterialMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.TrustDomainMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.UserBindingCriterionMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import lombok.CustomLog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;

/**
 * @author GraviteeSource Team
 */
@Component
@CustomLog
public class MongoTrustDomainRepository extends AbstractManagementMongoRepository implements TrustDomainRepository {

    private static final String COLLECTION_NAME = "trust_domains";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_SPIFFE_TRUST_DOMAIN = "spiffeTrustDomain";
    private static final String FIELD_ISSUER = "issuer";

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JWKModule());

    private MongoCollection<TrustDomainMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection(COLLECTION_NAME, TrustDomainMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(
                new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_NAME, 1),
                new IndexOptions().name("rt1ri1n1").unique(true),
                new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_SPIFFE_TRUST_DOMAIN, 1),
                new IndexOptions().name("rt1ri1std1").unique(true)
                        .partialFilterExpression(exists(FIELD_SPIFFE_TRUST_DOMAIN, true)),
                new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_ISSUER, 1),
                new IndexOptions().name("rt1ri1i1").unique(true)
                        .partialFilterExpression(exists(FIELD_ISSUER, true))
        ));
        if (ensureIndexOnStart) {
            stampSpiffeTrustDomainOnLegacyDocuments().subscribe();
        }
    }

    /**
     * Trust domains stored before the SPIFFE matcher was split out of the name are trusted for SPIFFE
     * under their name. Copying it onto the matcher puts them inside the matcher-scoped unique index,
     * which the SPIFFE lookup now goes through.
     */
    private Completable stampSpiffeTrustDomainOnLegacyDocuments() {
        return Completable.fromPublisher(collection.updateMany(
                        and(exists(FIELD_SPIFFE_TRUST_DOMAIN, false), exists(FIELD_ISSUER, false)),
                        List.of(new Document("$set", new Document(FIELD_SPIFFE_TRUST_DOMAIN, "$" + FIELD_NAME)))))
                .doOnError(error -> log.warn("Unable to stamp the SPIFFE trust domain on legacy trust domains", error));
    }

    @Override
    public Maybe<TrustDomain> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<TrustDomain> create(TrustDomain item) {
        TrustDomainMongo doc = toMongo(item);
        doc.setId(doc.getId() == null ? RandomString.generate() : doc.getId());
        return Single.fromPublisher(collection.insertOne(doc))
                .map(success -> {
                    item.setId(doc.getId());
                    return item;
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<TrustDomain> update(TrustDomain item) {
        TrustDomainMongo doc = toMongo(item);
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
    public Flowable<TrustDomain> findByReference(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(collection.find(and(
                        eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                        eq(FIELD_REFERENCE_ID, referenceId))))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<TrustDomain> findByName(ReferenceType referenceType, String referenceId, String name) {
        return findByField(referenceType, referenceId, FIELD_NAME, name);
    }

    @Override
    public Maybe<TrustDomain> findBySpiffeTrustDomain(ReferenceType referenceType, String referenceId, String spiffeTrustDomain) {
        return findByField(referenceType, referenceId, FIELD_SPIFFE_TRUST_DOMAIN, spiffeTrustDomain);
    }

    @Override
    public Maybe<TrustDomain> findByIssuer(ReferenceType referenceType, String referenceId, String issuer) {
        return findByField(referenceType, referenceId, FIELD_ISSUER, issuer);
    }

    private Maybe<TrustDomain> findByField(ReferenceType referenceType, String referenceId, String field, String value) {
        return Observable.fromPublisher(collection.find(and(
                        eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                        eq(FIELD_REFERENCE_ID, referenceId),
                        eq(field, value))).first())
                .firstElement()
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    private TrustDomain toEntity(TrustDomainMongo doc) {
        if (doc == null) {
            return null;
        }
        TrustDomain td = new TrustDomain();
        td.setId(doc.getId());
        td.setReferenceId(doc.getReferenceId());
        td.setReferenceType(doc.getReferenceType() != null ? ReferenceType.valueOf(doc.getReferenceType()) : null);
        td.setName(doc.getName());
        td.setDescription(doc.getDescription());
        td.setDomainIdentifier(doc.getIssuer());
        td.setKeyMaterial(readKeyMaterial(doc));
        td.setSpiffe(readSpiffe(doc));
        td.setTokenExchange(readTokenExchange(doc));
        td.setCreatedAt(doc.getCreatedAt());
        td.setUpdatedAt(doc.getUpdatedAt());
        return td;
    }

    private TrustDomainMongo toMongo(TrustDomain td) {
        if (td == null) {
            return null;
        }
        TrustDomainMongo doc = new TrustDomainMongo();
        doc.setId(td.getId());
        doc.setReferenceId(td.getReferenceId());
        doc.setReferenceType(td.getReferenceType() != null ? td.getReferenceType().name() : null);
        doc.setName(td.getName());
        doc.setDescription(td.getDescription());
        doc.setSpiffeTrustDomain(td.getSpiffeTrustDomain());
        doc.setIssuer(td.getIssuer());
        doc.setKeyMaterial(toMongo(td.getKeyMaterial()));
        doc.setRefreshIntervalSeconds(td.getRefreshIntervalSeconds());
        doc.setAllowedAlgorithms(td.getAllowedAlgorithms());
        doc.setScopeMappings(td.getScopeMappings());
        doc.setUserBindingEnabled(td.isUserBindingEnabled());
        doc.setUserBindingCriteria(UserBindingCriterionMongo.fromModelList(td.getUserBindingCriteria()));
        doc.setCreatedAt(td.getCreatedAt());
        doc.setUpdatedAt(td.getUpdatedAt());
        return doc;
    }

    /**
     * Reads the SPIFFE matcher, falling back to the name for trust domains stored while the name was
     * the matcher. Documents that carry an issuer were written by the migration and are not SPIFFE.
     */
    static String readSpiffeTrustDomain(TrustDomainMongo doc) {
        if (doc.getSpiffeTrustDomain() != null) {
            return doc.getSpiffeTrustDomain();
        }
        return doc.getIssuer() == null ? doc.getName() : null;
    }

    /**
     * Reads the shared key-material shape, falling back to the legacy bundle-source fields for
     * trust domains stored before it existed.
     */
    static TrustDomainKeyMaterial readKeyMaterial(TrustDomainMongo doc) {
        TrustDomainKeyMaterialMongo keyMaterial = doc.getKeyMaterial();
        TrustDomainKeyMaterial model = keyMaterial != null
                ? TrustDomainKeyMaterial.builder()
                        .source(keyMaterial.getSource() != null ? KeyMaterialSource.valueOf(keyMaterial.getSource()) : null)
                        .jwksUrl(keyMaterial.getJwksUrl())
                        .jwkSet(parseJwkSet(keyMaterial.getJwkSet()))
                        .certificate(keyMaterial.getCertificate())
                        .build()
                : TrustDomainKeyMaterial.fromBundleSource(
                        doc.getBundleSource() != null ? SpiffeBundleSource.valueOf(doc.getBundleSource()) : null,
                        doc.getJwksUrl());
        if (model != null) {
            model.setRefreshIntervalSeconds(doc.getRefreshIntervalSeconds());
        }
        return model;
    }

    static SpiffeTrustSettings readSpiffe(TrustDomainMongo doc) {
        String spiffeTrustDomain = readSpiffeTrustDomain(doc);
        if (spiffeTrustDomain == null && doc.getAllowedAlgorithms() == null) {
            return null;
        }
        return SpiffeTrustSettings.builder()
                .spiffeTrustDomain(spiffeTrustDomain)
                .allowedAlgorithms(doc.getAllowedAlgorithms())
                .build();
    }

    static TokenExchangeTrustSettings readTokenExchange(TrustDomainMongo doc) {
        if (doc.getIssuer() == null) {
            return null;
        }
        return TokenExchangeTrustSettings.builder()
                .scopeMappings(doc.getScopeMappings())
                .userBindingEnabled(Boolean.TRUE.equals(doc.getUserBindingEnabled()))
                .userBindingCriteria(UserBindingCriterionMongo.toModelList(doc.getUserBindingCriteria()))
                .build();
    }

    private static TrustDomainKeyMaterialMongo toMongo(TrustDomainKeyMaterial keyMaterial) {
        if (keyMaterial == null) {
            return null;
        }
        TrustDomainKeyMaterialMongo doc = new TrustDomainKeyMaterialMongo();
        doc.setSource(keyMaterial.getSource() != null ? keyMaterial.getSource().name() : null);
        doc.setJwksUrl(keyMaterial.getJwksUrl());
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
