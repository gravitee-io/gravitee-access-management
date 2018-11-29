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
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.KeyType;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.repository.management.api.ClientRepository;
import io.gravitee.am.repository.mongodb.common.IdGenerator;
import io.gravitee.am.repository.mongodb.common.LoggableIndexSubscriber;
import io.gravitee.am.repository.mongodb.management.internal.model.ClientMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.JWKMongo;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoClientRepository extends AbstractManagementMongoRepository implements ClientRepository {

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
        clientsCollection.createIndex(new Document(FIELD_DOMAIN, 1)).subscribe(new LoggableIndexSubscriber());
        clientsCollection.createIndex(new Document(FIELD_DOMAIN, 1).append(FIELD_CLIENT_ID, 1)).subscribe(new LoggableIndexSubscriber());
        clientsCollection.createIndex(new Document(FIELD_DOMAIN, 1).append(FIELD_GRANT_TYPES, 1)).subscribe(new LoggableIndexSubscriber());
        clientsCollection.createIndex(new Document(FIELD_IDENTITIES, 1)).subscribe(new LoggableIndexSubscriber());
        clientsCollection.createIndex(new Document(FIELD_CERTIFICATE, 1)).subscribe(new LoggableIndexSubscriber());
        clientsCollection.createIndex(new Document(FIELD_GRANT_TYPES, 1)).subscribe(new LoggableIndexSubscriber());
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
    public Single<Set<Client>> findByDomainAndExtensionGrant(String domain, String tokenGranter) {
        return Observable.fromPublisher(clientsCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_GRANT_TYPES, tokenGranter)))).map(this::convert).collect(HashSet::new, Set::add);
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
    public Completable delete(String id) {
        return Completable.fromPublisher(clientsCollection.deleteOne(eq(FIELD_ID, id)));
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
        client.setResponseTypes(clientMongo.getResponseTypes());
        client.setApplicationType(clientMongo.getApplicationType());
        client.setContacts(clientMongo.getContacts());
        client.setClientName(clientMongo.getClientName());
        client.setLogoUri(clientMongo.getLogoUri());
        client.setClientUri(clientMongo.getClientUri());
        client.setPolicyUri(clientMongo.getPolicyUri());
        client.setTosUri(clientMongo.getTosUri());
        client.setJwksUri(clientMongo.getJwksUri());
        client.setJwks(this.convert(clientMongo.getJwks()));
        client.setSectorIdentifierUri(clientMongo.getSectorIdentifierUri());
        client.setSubjectType(clientMongo.getSubjectType());
        client.setIdTokenSignedResponseAlg(clientMongo.getIdTokenSignedResponseAlg());
        client.setIdTokenEncryptedResponseAlg(clientMongo.getIdTokenEncryptedResponseAlg());
        client.setIdTokenEncryptedResponseEnc(clientMongo.getIdTokenEncryptedResponseEnc());
        client.setUserinfoSignedResponseAlg(clientMongo.getUserinfoSignedResponseAlg());
        client.setUserinfoEncryptedResponseAlg(clientMongo.getUserinfoEncryptedResponseAlg());
        client.setUserinfoEncryptedResponseEnc(clientMongo.getUserinfoEncryptedResponseEnc());
        client.setRequestObjectSigningAlg(clientMongo.getRequestObjectSigningAlg());
        client.setRequestObjectEncryptionAlg(clientMongo.getRequestObjectEncryptionAlg());
        client.setRequestObjectEncryptionEnc(clientMongo.getRequestObjectEncryptionEnc());
        client.setTokenEndpointAuthMethod(clientMongo.getTokenEndpointAuthMethod());
        client.setTokenEndpointAuthSigningAlg(clientMongo.getTokenEndpointAuthSigningAlg());
        client.setDefaultMaxAge(clientMongo.getDefaultMaxAge());
        client.setRequireAuthTime(clientMongo.getRequireAuthTime());
        client.setDefaultACRvalues(clientMongo.getDefaultACRvalues());
        client.setInitiateLoginUri( clientMongo.getInitiateLoginUri());
        client.setRequestUris(clientMongo.getRequestUris());
        client.setScopes(clientMongo.getScopes());
        client.setSoftwareId(clientMongo.getSoftwareId());
        client.setSoftwareVersion(clientMongo.getSoftwareVersion());
        client.setSoftwareStatement(clientMongo.getSoftwareStatement());
        client.setRegistrationAccessToken(clientMongo.getRegistrationAccessToken());
        client.setRegistrationClientUri(clientMongo.getRegistrationClientUri());
        client.setClientIdIssuedAt(clientMongo.getClientIdIssuedAt());
        client.setClientSecretExpiresAt(clientMongo.getClientSecretExpiresAt());
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
        clientMongo.setResponseTypes(client.getResponseTypes());
        clientMongo.setApplicationType(client.getApplicationType());
        clientMongo.setContacts(client.getContacts());
        clientMongo.setClientName(client.getClientName());
        clientMongo.setLogoUri(client.getLogoUri());
        clientMongo.setClientUri(client.getClientUri());
        clientMongo.setPolicyUri(client.getPolicyUri());
        clientMongo.setTosUri(client.getTosUri());
        clientMongo.setJwksUri(client.getJwksUri());
        clientMongo.setJwks(this.convert(client.getJwks()));
        clientMongo.setSectorIdentifierUri(client.getSectorIdentifierUri());
        clientMongo.setSubjectType(client.getSubjectType());
        clientMongo.setIdTokenSignedResponseAlg(client.getIdTokenSignedResponseAlg());
        clientMongo.setIdTokenEncryptedResponseAlg(client.getIdTokenEncryptedResponseAlg());
        clientMongo.setIdTokenEncryptedResponseEnc(client.getIdTokenEncryptedResponseEnc());
        clientMongo.setUserinfoSignedResponseAlg(client.getUserinfoSignedResponseAlg());
        clientMongo.setUserinfoEncryptedResponseAlg(client.getUserinfoEncryptedResponseAlg());
        clientMongo.setUserinfoEncryptedResponseEnc(client.getUserinfoEncryptedResponseEnc());
        clientMongo.setRequestObjectSigningAlg(client.getRequestObjectSigningAlg());
        clientMongo.setRequestObjectEncryptionAlg(client.getRequestObjectEncryptionAlg());
        clientMongo.setRequestObjectEncryptionEnc(client.getRequestObjectEncryptionEnc());
        clientMongo.setTokenEndpointAuthMethod(client.getTokenEndpointAuthMethod());
        clientMongo.setTokenEndpointAuthSigningAlg(client.getTokenEndpointAuthSigningAlg());
        clientMongo.setDefaultMaxAge(client.getDefaultMaxAge());
        clientMongo.setRequireAuthTime(client.getRequireAuthTime());
        clientMongo.setDefaultACRvalues(client.getDefaultACRvalues());
        clientMongo.setInitiateLoginUri(client.getInitiateLoginUri());
        clientMongo.setRequestUris(client.getRequestUris());
        clientMongo.setScopes(client.getScopes());
        clientMongo.setSoftwareId(client.getSoftwareId());
        clientMongo.setSoftwareVersion(client.getSoftwareVersion());
        clientMongo.setSoftwareStatement(client.getSoftwareStatement());
        clientMongo.setRegistrationAccessToken(client.getRegistrationAccessToken());
        clientMongo.setRegistrationClientUri(client.getRegistrationClientUri());
        clientMongo.setClientIdIssuedAt(client.getClientIdIssuedAt());
        clientMongo.setClientSecretExpiresAt(client.getClientSecretExpiresAt());
        clientMongo.setAutoApproveScopes(client.getAutoApproveScopes());
        clientMongo.setEnabled(client.isEnabled());
        clientMongo.setIdentities(client.getIdentities());
        clientMongo.setOauth2Identities(client.getOauth2Identities());
        clientMongo.setDomain(client.getDomain());
        clientMongo.setIdTokenValiditySeconds(client.getIdTokenValiditySeconds());
        clientMongo.setIdTokenCustomClaims(client.getIdTokenCustomClaims() != null ? new Document(client.getIdTokenCustomClaims()) : new Document());
        clientMongo.setCertificate(client.getCertificate());
        clientMongo.setEnhanceScopesWithUserPermissions(client.isEnhanceScopesWithUserPermissions());
        clientMongo.setCreatedAt(client.getCreatedAt());
        clientMongo.setUpdatedAt(client.getUpdatedAt());
        return clientMongo;
    }

    private JWKSet convert(List<JWKMongo> jwksMongo) {
        if (jwksMongo==null) {
            return null;
        }

        JWKSet jwkSet = new JWKSet();

        List<JWK> jwkList = jwksMongo.stream()
                .map(jwkMongo -> this.convert(jwkMongo))
                .collect(Collectors.toList());

        jwkSet.setKeys(jwkList);

        return jwkSet;
    }

    private JWK convert(JWKMongo jwkMongo) {
        if (jwkMongo==null) {
            return null;
        }

        JWK result;

        switch (KeyType.valueOf(jwkMongo.getKty())) {
            case EC: result = convertEC(jwkMongo);break;
            case RSA:result = convertRSA(jwkMongo);break;
            case OCT:result = null;break;//TODO
            case OKP:result = null;break;//TODO
            default: result = null;//TODO
        }

        result.setAlg(jwkMongo.getAlg());
        result.setKeyOps(jwkMongo.getKeyOps()!=null?jwkMongo.getKeyOps().stream().collect(Collectors.toSet()):null);
        result.setKid(jwkMongo.getKid());
        result.setKty(jwkMongo.getKty());
        result.setUse(jwkMongo.getUse());
        result.setX5c(jwkMongo.getX5c()!=null?jwkMongo.getX5c().stream().collect(Collectors.toSet()):null);
        result.setX5t(jwkMongo.getX5t());
        result.setX5tS256(jwkMongo.getX5tS256());
        result.setX5u(jwkMongo.getX5u());

        return result;
    }

    private RSAKey convertRSA(JWKMongo rsaKeyMongo) {
        RSAKey key = new RSAKey();
        key.setE(rsaKeyMongo.getE());
        key.setN(rsaKeyMongo.getN());
        return key;
    }

    private ECKey convertEC(JWKMongo ecKeyMongo) {
        ECKey key = new ECKey();
        key.setCrv(ecKeyMongo.getCrv());
        key.setX(ecKeyMongo.getX());
        key.setY(ecKeyMongo.getY());
        return key;
    }

    private List<JWKMongo> convert(JWKSet jwkSet) {
        if (jwkSet==null) {
            return null;
        }

        return jwkSet.getKeys().stream()
                .map(jwk -> this.convert(jwk))
                .collect(Collectors.toList());
    }

    private JWKMongo convert(JWK jwk) {
        if (jwk==null) {
            return null;
        }

        JWKMongo result;

        switch (KeyType.valueOf(jwk.getKty())) {
            case EC: result = convert((ECKey)jwk);break;
            case RSA:result = convert((RSAKey)jwk);break;
            case OCT:result = new JWKMongo();break;//TODO
            case OKP:result = new JWKMongo();break;//TODO
            default: result = new JWKMongo();//TODO
        }

        result.setAlg(jwk.getAlg());
        result.setKeyOps(jwk.getKeyOps()!=null?jwk.getKeyOps().stream().collect(Collectors.toList()):null);
        result.setKid(jwk.getKid());
        result.setKty(jwk.getKty());
        result.setUse(jwk.getUse());
        result.setX5c(jwk.getX5c()!=null?jwk.getX5c().stream().collect(Collectors.toList()):null);
        result.setX5t(jwk.getX5t());
        result.setX5tS256(jwk.getX5tS256());
        result.setX5u(jwk.getX5u());

        return result;
    }

    private JWKMongo convert(RSAKey rsaKey) {
        JWKMongo key = new JWKMongo();
        key.setE(rsaKey.getE());
        key.setN(rsaKey.getN());
        return key;
    }

    private JWKMongo convert(ECKey ecKey) {
        JWKMongo key = new JWKMongo();
        key.setCrv(ecKey.getCrv());
        key.setX(ecKey.getX());
        key.setY(ecKey.getY());
        return key;
    }
}
