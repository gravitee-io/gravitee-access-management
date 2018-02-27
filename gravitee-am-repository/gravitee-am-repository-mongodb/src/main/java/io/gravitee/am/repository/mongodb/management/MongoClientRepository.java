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
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.ClientRepository;
import io.gravitee.am.repository.mongodb.common.IdGenerator;
import io.gravitee.am.repository.mongodb.management.internal.model.ClientMongo;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subscribers.DefaultSubscriber;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

import static com.mongodb.client.model.Filters.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoClientRepository extends AbstractManagementMongoRepository implements ClientRepository {

    private static final Logger logger = LoggerFactory.getLogger(MongoClientRepository.class);
    private static final String FIELD_ID = "_id";
    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_CLIENT_ID = "clientId";
    private static final String FIELD_IDENTITIES = "identities";
    private static final String FIELD_OAUTH2_IDENTITIES = "oauth2Identities";
    private static final String FIELD_CERTIFICATE = "certificate";
    private static final String FIELD_GRANT_TYPES= "authorizedGrantTypes";
    private MongoCollection<ClientMongo> clientsCollection;

    @Autowired
    private IdGenerator idGenerator;

    @PostConstruct
    public void init() {
        clientsCollection = mongoOperations.getCollection("clients", ClientMongo.class);
        clientsCollection.createIndex(new Document(FIELD_DOMAIN, 1)).subscribe(new IndexSubscriber());
        clientsCollection.createIndex(new Document(FIELD_DOMAIN, 1).append(FIELD_CLIENT_ID, 1)).subscribe(new IndexSubscriber());
        clientsCollection.createIndex(new Document(FIELD_IDENTITIES, 1)).subscribe(new IndexSubscriber());
        clientsCollection.createIndex(new Document(FIELD_CERTIFICATE, 1)).subscribe(new IndexSubscriber());
        clientsCollection.createIndex(new Document(FIELD_GRANT_TYPES, 1)).subscribe(new IndexSubscriber());
    }

    @Override
    public Single<Set<Client>> findByDomain(String domain) {
        return Observable.fromPublisher(clientsCollection.find(eq(FIELD_DOMAIN, domain))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Page<Client>> findByDomain(String domain, int page, int size) {
        Single<Long> countOperation = Observable.fromPublisher(clientsCollection.count(eq(FIELD_DOMAIN, domain))).first(0l);
        Single<Set<Client>> clientsOperation = Observable.fromPublisher(clientsCollection.find(eq(FIELD_DOMAIN, domain)).skip(size * (page - 1)).limit(size)).map(this::convert).collect(HashSet::new, Set::add);
        return Single.zip(countOperation, clientsOperation, (count, clients) -> new Page<>(clients, page, count));
    }

    @Override
    public Maybe<Client> findByClientIdAndDomain(String clientId, String domain) {
        return Observable.fromPublisher(clientsCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_CLIENT_ID, clientId))).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<Set<Client>> findByIdentityProvider(String identityProvider) {
        return Observable.fromPublisher(clientsCollection.find(or(eq(FIELD_IDENTITIES, identityProvider), eq(FIELD_OAUTH2_IDENTITIES, identityProvider)))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Set<Client>> findByCertificate(String certificate) {
        return Observable.fromPublisher(clientsCollection.find(eq(FIELD_CERTIFICATE, certificate))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Set<Client>> findByExtensionGrant(String tokenGranter) {
        return Observable.fromPublisher(clientsCollection.find(eq(FIELD_GRANT_TYPES, tokenGranter))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Set<Client>> findAll() {
        return Observable.fromPublisher(clientsCollection.find()).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Page<Client>> findAll(int page, int size) {
        Single<Long> countOperation = Observable.fromPublisher(clientsCollection.count()).first(0l);
        Single<Set<Client>> clientsOperation = Observable.fromPublisher(clientsCollection.find().skip(size * (page - 1)).limit(size)).map(this::convert).collect(HashSet::new, Set::add);
        return Single.zip(countOperation, clientsOperation, (count, clients) -> new Page<>(clients, page, count));
    }

    @Override
    public Maybe<Client> findById(String client) {
        return Observable.fromPublisher(clientsCollection.find(eq(FIELD_ID, client)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<Client> create(Client item) {
        ClientMongo client = convert(item);
        client.setId(client.getId() == null ? (String) idGenerator.generate() : client.getId());
        return Single.fromPublisher(clientsCollection.insertOne(client)).flatMap(success -> findById(client.getId()).toSingle());
    }

    @Override
    public Single<Client> update(Client item) {
        ClientMongo client = convert(item);
        return Single.fromPublisher(clientsCollection.replaceOne(eq(FIELD_ID, client.getId()), client)).flatMap(success -> findById(client.getId()).toSingle());
    }

    @Override
    public Single<Irrelevant> delete(String id) {
        return Single.fromPublisher(clientsCollection.deleteOne(eq(FIELD_ID, id))).map(deleteResult -> Irrelevant.CLIENT);
    }

    @Override
    public Single<Long> countByDomain(String domain) {
        return Observable.fromPublisher(clientsCollection.count(eq(FIELD_DOMAIN, domain))).first(0l);
    }

    @Override
    public Single<Long> count() {
        return Observable.fromPublisher(clientsCollection.count()).first(0l);
    }

    private Client convert(ClientMongo clientMongo) {
        if (clientMongo == null) {
            return null;
        }

        Client client = new Client();
        client.setId(clientMongo.getId());
        client.setClientId(clientMongo.getClientId());
        client.setClientSecret(clientMongo.getClientSecret());
        client.setAccessTokenValiditySeconds(clientMongo.getAccessTokenValiditySeconds());
        client.setRefreshTokenValiditySeconds(clientMongo.getRefreshTokenValiditySeconds());
        client.setRedirectUris(clientMongo.getRedirectUris());
        client.setScopes(clientMongo.getScopes());
        client.setAutoApproveScopes(clientMongo.getAutoApproveScopes());
        client.setEnabled(clientMongo.isEnabled());
        client.setIdentities(clientMongo.getIdentities());
        client.setOauth2Identities(clientMongo.getOauth2Identities());
        client.setDomain(clientMongo.getDomain());
        client.setAuthorizedGrantTypes(clientMongo.getAuthorizedGrantTypes());
        client.setIdTokenValiditySeconds(clientMongo.getIdTokenValiditySeconds());
        client.setIdTokenCustomClaims(clientMongo.getIdTokenCustomClaims());
        client.setCertificate(clientMongo.getCertificate());
        client.setEnhanceScopesWithUserPermissions(clientMongo.isEnhanceScopesWithUserPermissions());
        client.setGenerateNewTokenPerRequest(clientMongo.isGenerateNewTokenPerRequest());
        client.setCreatedAt(clientMongo.getCreatedAt());
        client.setUpdatedAt(clientMongo.getUpdatedAt());
        return client;
    }

    private ClientMongo convert(Client client) {
        if (client == null) {
            return null;
        }

        ClientMongo clientMongo = new ClientMongo();
        clientMongo.setId(client.getId());
        clientMongo.setClientId(client.getClientId());
        clientMongo.setClientSecret(client.getClientSecret());
        clientMongo.setAccessTokenValiditySeconds(client.getAccessTokenValiditySeconds());
        clientMongo.setRefreshTokenValiditySeconds(client.getRefreshTokenValiditySeconds());
        clientMongo.setRedirectUris(client.getRedirectUris());
        clientMongo.setAuthorizedGrantTypes(client.getAuthorizedGrantTypes());
        clientMongo.setScopes(client.getScopes());
        clientMongo.setAutoApproveScopes(client.getAutoApproveScopes());
        clientMongo.setEnabled(client.isEnabled());
        clientMongo.setIdentities(client.getIdentities());
        clientMongo.setOauth2Identities(client.getOauth2Identities());
        clientMongo.setDomain(client.getDomain());
        clientMongo.setIdTokenValiditySeconds(client.getIdTokenValiditySeconds());
        clientMongo.setIdTokenCustomClaims(client.getIdTokenCustomClaims() != null ? new Document(client.getIdTokenCustomClaims()) : new Document());
        clientMongo.setCertificate(client.getCertificate());
        clientMongo.setEnhanceScopesWithUserPermissions(client.isEnhanceScopesWithUserPermissions());
        clientMongo.setGenerateNewTokenPerRequest(client.isGenerateNewTokenPerRequest());
        clientMongo.setCreatedAt(client.getCreatedAt());
        clientMongo.setUpdatedAt(client.getUpdatedAt());
        return clientMongo;
    }

    private class IndexSubscriber extends DefaultSubscriber<String> {
        @Override
        public void onNext(String value) {
            logger.debug("Created an index named : " + value);
        }

        @Override
        public void onError(Throwable throwable) {
            logger.error("Error occurs during indexing", throwable);
        }

        @Override
        public void onComplete() {
            logger.debug("Index creation complete");
        }
    }
}
