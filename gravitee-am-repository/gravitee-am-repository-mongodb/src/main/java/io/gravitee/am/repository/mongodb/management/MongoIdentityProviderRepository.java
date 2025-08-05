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
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.IdentityProviderMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.BsonArray;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoIdentityProviderRepository extends AbstractManagementMongoRepository implements IdentityProviderRepository {

    public static final String FIELD_PASSWORD_POLICY = "passwordPolicy";

    private MongoCollection<IdentityProviderMongo> identitiesCollection;

    @PostConstruct
    public void init() {
        identitiesCollection = mongoOperations.getCollection("identities", IdentityProviderMongo.class);
        super.init(identitiesCollection);
        super.createIndex(identitiesCollection, Map.of(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1), new IndexOptions().name("rt1ri1")));
    }

    @Override
    public Flowable<IdentityProvider> findAll(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(withMaxTime(identitiesCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId)))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<IdentityProvider> findAll(ReferenceType referenceType) {
        return Flowable.fromPublisher(withMaxTime(identitiesCollection.find(eq(FIELD_REFERENCE_TYPE, referenceType.name()))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<IdentityProvider> findAll() {
        return Flowable.fromPublisher(withMaxTime(identitiesCollection.find())).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<IdentityProvider> findById(String identityProviderId) {
        return Observable.fromPublisher(identitiesCollection.find(eq(FIELD_ID, identityProviderId)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<IdentityProvider> findById(ReferenceType referenceType, String referenceId, String identityProviderId) {
        return Observable.fromPublisher(identitiesCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_ID, identityProviderId))).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<IdentityProvider> findAllByPasswordPolicy(ReferenceType referenceType, String referenceId, String passwordPolicy) {
        return Flowable.fromPublisher(withMaxTime(identitiesCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_PASSWORD_POLICY, passwordPolicy)))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<IdentityProvider> create(IdentityProvider item) {
        Optional<IdentityProviderMongo> optionalIdp = convert(item);
        if (optionalIdp.isPresent()) {
            var identityProvider = optionalIdp.get();
            identityProvider.setId(identityProvider.getId() == null ? RandomString.generate() : identityProvider.getId());
            return Single.fromPublisher(identitiesCollection.insertOne(identityProvider)).flatMap(success -> {
                item.setId(identityProvider.getId());
                return Single.just(item);
            })
                    .observeOn(Schedulers.computation());
        }
        return Single.error(new TechnicalException("Identity provider must be present for create"));
    }

    @Override
    public Single<IdentityProvider> update(IdentityProvider item) {
        Optional<IdentityProviderMongo> optionalIdp = convert(item);
        if (optionalIdp.isPresent()) {
            var identityProvider = optionalIdp.get();
            return Single.fromPublisher(identitiesCollection.replaceOne(eq(FIELD_ID, identityProvider.getId()), identityProvider)).flatMap(updateResult -> Single.just(item))
                    .observeOn(Schedulers.computation());
        }
        return Single.error(new TechnicalException("Identity provider must be present for update"));
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(identitiesCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private IdentityProvider convert(IdentityProviderMongo identityProviderMongo) {
        if (isNull(identityProviderMongo)) {
            return null;
        }

        var identityProvider = new IdentityProvider();
        identityProvider.setId(identityProviderMongo.getId());
        identityProvider.setName(identityProviderMongo.getName());
        identityProvider.setDataPlaneId(identityProviderMongo.getDataPlaneId());
        identityProvider.setType(identityProviderMongo.getType());
        identityProvider.setSystem(identityProviderMongo.isSystem());
        identityProvider.setConfiguration(identityProviderMongo.getConfiguration());
        identityProvider.setMappers((Map) identityProviderMongo.getMappers());

        if (identityProviderMongo.getRoleMapper() != null) {
            Map<String, String[]> roleMapper = new HashMap<>(identityProviderMongo.getRoleMapper().size());
            identityProviderMongo.getRoleMapper().forEach((key, value) -> {
                List lstValue = (List) value;
                String[] arr = new String[lstValue.size()];
                lstValue.toArray(arr);
                roleMapper.put(key, arr);
            });
            identityProvider.setRoleMapper(roleMapper);
        }

        Map<String, String[]> groupMappers = ofNullable(identityProviderMongo.getGroupMapper())
                .orElseGet(Document::new)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    List<?> lstValue = (List<?>) entry.getValue();
                    return lstValue.toArray(new String[0]);
                }));

        identityProvider.setGroupMapper(groupMappers);

        identityProvider.setReferenceType(identityProviderMongo.getReferenceType());
        identityProvider.setReferenceId(identityProviderMongo.getReferenceId());
        identityProvider.setExternal(identityProviderMongo.isExternal());
        identityProvider.setDomainWhitelist(
                ofNullable(identityProviderMongo.getDomainWhitelist()).orElse(new BsonArray())
                        .stream().map(BsonValue::asString).map(BsonString::getValue)
                        .toList());
        identityProvider.setCreatedAt(identityProviderMongo.getCreatedAt());
        identityProvider.setUpdatedAt(identityProviderMongo.getUpdatedAt());
        identityProvider.setPasswordPolicy(identityProviderMongo.getPasswordPolicy());
        return identityProvider;
    }

    private Optional<IdentityProviderMongo> convert(IdentityProvider identityProvider) {
        return ofNullable(identityProvider).map(Objects::nonNull).map(idp -> {
            var identityProviderMongo = new IdentityProviderMongo();
            identityProviderMongo.setId(identityProvider.getId());
            identityProviderMongo.setName(identityProvider.getName());
            identityProviderMongo.setType(identityProvider.getType());
            identityProviderMongo.setDataPlaneId(identityProvider.getDataPlaneId());
            identityProviderMongo.setSystem(identityProvider.isSystem());
            identityProviderMongo.setConfiguration(identityProvider.getConfiguration());
            identityProviderMongo.setReferenceType(identityProvider.getReferenceType());
            identityProviderMongo.setReferenceId(identityProvider.getReferenceId());
            identityProviderMongo.setExternal(identityProvider.isExternal());
            identityProviderMongo.setCreatedAt(identityProvider.getCreatedAt());
            identityProviderMongo.setUpdatedAt(identityProvider.getUpdatedAt());
            var mappers = new Document(ofNullable(identityProvider.getMappers()).filter(Objects::nonNull).orElse(Map.of()));
            var roleMapper = new Document(convert(ofNullable(identityProvider.getRoleMapper()).filter(Objects::nonNull).orElse(Map.of())));
            var groupMapper = new Document(convert(ofNullable(identityProvider.getGroupMapper()).filter(Objects::nonNull).orElse(Map.of())));
            identityProviderMongo.setMappers(mappers);
            identityProviderMongo.setRoleMapper(roleMapper);
            identityProviderMongo.setGroupMapper(groupMapper);
            identityProviderMongo.setDomainWhitelist(
                    ofNullable(identityProvider.getDomainWhitelist()).orElse(List.of()).stream()
                            .map(BsonString::new)
                            .collect(toCollection(BsonArray::new)));
            identityProviderMongo.setPasswordPolicy(identityProvider.getPasswordPolicy());
            return identityProviderMongo;
        });
    }

    private Document convert(Map<String, String[]> map) {
        Document document = new Document();
        map.forEach((k, v) -> document.append(k, Arrays.asList(v)));
        return document;
    }
}
