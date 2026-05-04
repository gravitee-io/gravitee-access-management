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
package io.gravitee.am.repository.mongodb.oauth2;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.repository.mongodb.oauth2.internal.model.AAuthBootstrapBindingMongo;
import io.gravitee.am.repository.oidc.api.AAuthBootstrapBindingRepository;
import io.gravitee.am.repository.oidc.model.AAuthBootstrapBinding;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;

/**
 * MongoDB repository for AAUTH bootstrap bindings.
 * Follows the same pattern as {@link MongoAAuthPendingRequestRepository}.
 *
 * @author GraviteeSource Team
 */
@Component
public class MongoAAuthBootstrapBindingRepository extends AbstractOAuth2MongoRepository implements AAuthBootstrapBindingRepository {

    private static final String COLLECTION = "aauth_bootstrap_bindings";
    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_USER_ID = "user_id";
    private static final String FIELD_AGENT_SERVER_URL = "agent_server_url";

    private MongoCollection<AAuthBootstrapBindingMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection(COLLECTION, AAuthBootstrapBindingMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(
                new Document(FIELD_DOMAIN, 1).append(FIELD_USER_ID, 1), new IndexOptions().name("du1"),
                new Document(FIELD_DOMAIN, 1).append(FIELD_AGENT_SERVER_URL, 1).append(FIELD_USER_ID, 1), new IndexOptions().unique(true).name("dau1")
        ));
    }

    @Override
    public Maybe<AAuthBootstrapBinding> findById(String id) {
        return Observable
                .fromPublisher(collection.find(eq(FIELD_ID, id)).limit(1).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AAuthBootstrapBinding> findByDomainAndUserId(String domain, String userId) {
        return Flowable
                .fromPublisher(collection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_USER_ID, userId))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AAuthBootstrapBinding> findByDomainAndAgentServerUrlAndUserId(String domain, String agentServerUrl, String userId) {
        return Observable
                .fromPublisher(collection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_AGENT_SERVER_URL, agentServerUrl), eq(FIELD_USER_ID, userId))).limit(1).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthBootstrapBinding> create(AAuthBootstrapBinding binding) {
        binding.setId(binding.getId() == null ? SecureRandomString.generate() : binding.getId());
        return Single
                .fromPublisher(collection.insertOne(convert(binding)))
                .flatMap(success -> findById(binding.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthBootstrapBinding> update(AAuthBootstrapBinding binding) {
        return Single
                .fromPublisher(collection.replaceOne(eq(FIELD_ID, binding.getId()), convert(binding)))
                .flatMap(success -> findById(binding.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.findOneAndDelete(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private AAuthBootstrapBindingMongo convert(AAuthBootstrapBinding binding) {
        if (binding == null) return null;

        AAuthBootstrapBindingMongo mongo = new AAuthBootstrapBindingMongo();
        mongo.setId(binding.getId());
        mongo.setDomain(binding.getDomain());
        mongo.setUserId(binding.getUserId());
        mongo.setAgentServerUrl(binding.getAgentServerUrl());
        mongo.setAgentIdentifier(binding.getAgentIdentifier());
        mongo.setPairwiseSub(binding.getPairwiseSub());
        mongo.setCreatedAt(binding.getCreatedAt());
        mongo.setUpdatedAt(binding.getUpdatedAt());
        return mongo;
    }

    private AAuthBootstrapBinding convert(AAuthBootstrapBindingMongo mongo) {
        if (mongo == null) return null;

        AAuthBootstrapBinding binding = new AAuthBootstrapBinding();
        binding.setId(mongo.getId());
        binding.setDomain(mongo.getDomain());
        binding.setUserId(mongo.getUserId());
        binding.setAgentServerUrl(mongo.getAgentServerUrl());
        binding.setAgentIdentifier(mongo.getAgentIdentifier());
        binding.setPairwiseSub(mongo.getPairwiseSub());
        binding.setCreatedAt(mongo.getCreatedAt());
        binding.setUpdatedAt(mongo.getUpdatedAt());
        return binding;
    }
}
