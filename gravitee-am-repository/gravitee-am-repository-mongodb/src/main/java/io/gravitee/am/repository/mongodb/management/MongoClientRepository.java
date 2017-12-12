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

import io.gravitee.am.model.Client;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ClientRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.ClientMongo;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoClientRepository extends AbstractManagementMongoRepository implements ClientRepository {

    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_CLIENT_ID = "clientId";
    private static final String FIELD_IDENTITIES = "identities";
    private static final String FIELD_CERTIFICATE = "certificate";
    private static final String FIELD_GRANT_TYPES= "authorizedGrantTypes";

    @PostConstruct
    public void ensureIndexes() {
        mongoOperations.indexOps(ClientMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_DOMAIN, Sort.Direction.ASC));

        mongoOperations.indexOps(ClientMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_DOMAIN, Sort.Direction.ASC)
                        .on(FIELD_CLIENT_ID, Sort.Direction.ASC));

        mongoOperations.indexOps(ClientMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_IDENTITIES, Sort.Direction.ASC));

        mongoOperations.indexOps(ClientMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_CERTIFICATE, Sort.Direction.ASC));

        mongoOperations.indexOps(ClientMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_GRANT_TYPES, Sort.Direction.ASC));
    }

    @Override
    public Set<Client> findByDomain(String domain) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_DOMAIN).is(domain));

        return mongoOperations
                .find(query, ClientMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    @Override
    public Page<Client> findByDomain(String domain, int page, int size) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_DOMAIN).is(domain));
        query.with(new PageRequest(page, size));

        long totalCount = mongoOperations.count(query, ClientMongo.class);

        Set<Client> clients = mongoOperations
                .find(query, ClientMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());

        return new Page(clients, page, totalCount);
    }

    @Override
    public Optional<Client> findByClientIdAndDomain(String clientId, String domain) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(
                Criteria.where(FIELD_DOMAIN).is(domain)
                        .andOperator(Criteria.where(FIELD_CLIENT_ID).is(clientId)));

        ClientMongo client = mongoOperations.findOne(query, ClientMongo.class);
        return Optional.ofNullable(convert(client));
    }

    @Override
    public Set<Client> findByIdentityProvider(String identityProvider) {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_IDENTITIES).is(identityProvider));

        return mongoOperations
                .find(query, ClientMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Client> findByCertificate(String certificate) {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_CERTIFICATE).is(certificate));

        return mongoOperations
                .find(query, ClientMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Client> findByExtensionGrant(String tokenGranter) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_GRANT_TYPES).is(tokenGranter));

        return mongoOperations
                .find(query, ClientMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Client> findAll() throws TechnicalException {
        return mongoOperations
                .findAll(ClientMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    @Override
    public Page<Client> findAll(int page, int size) throws TechnicalException {
        Query query = new Query();
        query.with(new PageRequest(page, size));

        long totalCount = mongoOperations.count(query, ClientMongo.class);

        Set<Client> clients = mongoOperations
                .find(query, ClientMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());

        return new Page(clients, page, totalCount);
    }

    @Override
    public Optional<Client> findById(String client) throws TechnicalException {
        return Optional.ofNullable(convert(mongoOperations.findById(client, ClientMongo.class)));
    }

    @Override
    public Client create(Client item) throws TechnicalException {
        ClientMongo domain = convert(item);
        mongoOperations.save(domain);
        return convert(domain);
    }

    @Override
    public Client update(Client item) throws TechnicalException {
        ClientMongo client = convert(item);
        mongoOperations.save(client);
        return convert(client);
    }

    @Override
    public void delete(String id) throws TechnicalException {
        ClientMongo client = mongoOperations.findById(id, ClientMongo.class);
        mongoOperations.remove(client);
    }

    @Override
    public long countByDomain(String domain) {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_DOMAIN).is(domain));

        return mongoOperations.count(query, ClientMongo.class);
    }

    public long count() {
        return mongoOperations.getCollection( "clients").count();
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
        clientMongo.setDomain(client.getDomain());
        clientMongo.setIdTokenValiditySeconds(client.getIdTokenValiditySeconds());
        clientMongo.setIdTokenCustomClaims(client.getIdTokenCustomClaims());
        clientMongo.setCertificate(client.getCertificate());
        clientMongo.setEnhanceScopesWithUserPermissions(client.isEnhanceScopesWithUserPermissions());
        clientMongo.setGenerateNewTokenPerRequest(client.isGenerateNewTokenPerRequest());
        clientMongo.setCreatedAt(client.getCreatedAt());
        clientMongo.setUpdatedAt(client.getUpdatedAt());
        return clientMongo;
    }
}
