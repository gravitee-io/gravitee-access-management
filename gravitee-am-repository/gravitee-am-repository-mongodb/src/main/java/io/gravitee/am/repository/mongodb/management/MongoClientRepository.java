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
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.jose.*;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.repository.management.api.ClientRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AccountSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.ClientMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.JWKMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.TokenClaimMongo;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoClientRepository extends AbstractManagementMongoRepository implements ClientRepository {

    public static final String COLLECTION_NAME = "clients";

    @Override
    public Single<Set<Client>> findAll() {
        MongoCollection<ClientMongo> clientsCollection =  mongoOperations.getCollection(COLLECTION_NAME, ClientMongo.class);
        return Observable.fromPublisher(clientsCollection.find()).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Boolean> collectionExists() {
        return Observable.fromPublisher(mongoOperations.listCollectionNames())
                .filter(collectionName -> collectionName.equalsIgnoreCase(COLLECTION_NAME))
                .isEmpty()
                .map(isEmpty -> !isEmpty);
    }

    @Override
    public Completable deleteCollection() {
        return Completable.fromPublisher(mongoOperations.getCollection(COLLECTION_NAME).drop());
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
        client.setDomain(clientMongo.getDomain());
        client.setAuthorizedGrantTypes(clientMongo.getAuthorizedGrantTypes());
        client.setIdTokenValiditySeconds(clientMongo.getIdTokenValiditySeconds());
        client.setCertificate(clientMongo.getCertificate());
        client.setEnhanceScopesWithUserPermissions(clientMongo.isEnhanceScopesWithUserPermissions());
        client.setCreatedAt(clientMongo.getCreatedAt());
        client.setUpdatedAt(clientMongo.getUpdatedAt());
        client.setScopeApprovals((Map)clientMongo.getScopeApprovals());
        client.setAccountSettings(convert(clientMongo.getAccountSettings()));
        client.setTokenCustomClaims(getTokenClaims(clientMongo.getTokenCustomClaims()));
        client.setTemplate(clientMongo.isTemplate());
        client.setMetadata(clientMongo.getMetadata());
        client.setTlsClientAuthSanDns(clientMongo.getTlsClientAuthSanDns());
        client.setTlsClientAuthSanEmail(clientMongo.getTlsClientAuthSanEmail());
        client.setTlsClientAuthSanIp(clientMongo.getTlsClientAuthSanIp());
        client.setTlsClientAuthSanUri(clientMongo.getTlsClientAuthSanUri());
        client.setTlsClientAuthSubjectDn(clientMongo.getTlsClientAuthSubjectDn());
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
        clientMongo.setDomain(client.getDomain());
        clientMongo.setIdTokenValiditySeconds(client.getIdTokenValiditySeconds());
        clientMongo.setCertificate(client.getCertificate());
        clientMongo.setEnhanceScopesWithUserPermissions(client.isEnhanceScopesWithUserPermissions());
        clientMongo.setCreatedAt(client.getCreatedAt());
        clientMongo.setUpdatedAt(client.getUpdatedAt());
        clientMongo.setScopeApprovals(client.getScopeApprovals() != null ? new Document((Map)client.getScopeApprovals()) : new Document());
        clientMongo.setAccountSettings(convert(client.getAccountSettings()));
        clientMongo.setTokenCustomClaims(getMongoTokenClaims(client.getTokenCustomClaims()));
        clientMongo.setTemplate(client.isTemplate());
        clientMongo.setMetadata(client.getMetadata() != null ? new Document((Map)client.getMetadata()) : new Document());
        clientMongo.setTlsClientAuthSanDns(client.getTlsClientAuthSanDns());
        clientMongo.setTlsClientAuthSanEmail(client.getTlsClientAuthSanEmail());
        clientMongo.setTlsClientAuthSanIp(client.getTlsClientAuthSanIp());
        clientMongo.setTlsClientAuthSanUri(client.getTlsClientAuthSanUri());
        clientMongo.setTlsClientAuthSubjectDn(client.getTlsClientAuthSubjectDn());
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

        switch (KeyType.parse(jwkMongo.getKty())) {
            case EC: result = convertEC(jwkMongo);break;
            case RSA:result = convertRSA(jwkMongo);break;
            case OCT:result = convertOCT(jwkMongo);break;
            case OKP:result = convertOKP(jwkMongo);break;
            default: result = null;
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

    private OCTKey convertOCT(JWKMongo ecKeyMongo) {
        OCTKey key = new OCTKey();
        key.setK(ecKeyMongo.getK());
        return key;
    }

    private OKPKey convertOKP(JWKMongo ecKeyMongo) {
        OKPKey key = new OKPKey();
        key.setCrv(ecKeyMongo.getCrv());
        key.setX(ecKeyMongo.getX());
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

        switch (KeyType.parse(jwk.getKty())) {
            case EC: result = convert((ECKey)jwk);break;
            case RSA:result = convert((RSAKey)jwk);break;
            case OCT:result = convert((OCTKey)jwk);break;
            case OKP:result = convert((OKPKey)jwk);break;
            default: result = null;
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

    private JWKMongo convert(OKPKey okpKey) {
        JWKMongo key = new JWKMongo();
        key.setCrv(okpKey.getCrv());
        key.setX(okpKey.getX());
        return key;
    }

    private JWKMongo convert(OCTKey octKey) {
        JWKMongo key = new JWKMongo();
        key.setK(octKey.getK());
        return key;
    }

    private AccountSettings convert(AccountSettingsMongo accountSettingsMongo) {
        return accountSettingsMongo != null ? accountSettingsMongo.convert() : null;
    }

    private AccountSettingsMongo convert(AccountSettings accountSettings) {
        return AccountSettingsMongo.convert(accountSettings);
    }

    private List<TokenClaim> getTokenClaims(List<TokenClaimMongo> mongoTokenClaims) {
        if (mongoTokenClaims == null) {
            return null;
        }
        return mongoTokenClaims.stream().map(this::convert).collect(Collectors.toList());
    }

    private List<TokenClaimMongo> getMongoTokenClaims(List<TokenClaim> tokenClaims) {
        if (tokenClaims == null) {
            return null;
        }
        return tokenClaims.stream().map(this::convert).collect(Collectors.toList());
    }

    private TokenClaim convert(TokenClaimMongo mongoTokenClaim) {
        TokenClaim tokenClaim = new TokenClaim();
        tokenClaim.setTokenType(TokenTypeHint.from(mongoTokenClaim.getTokenType()));
        tokenClaim.setClaimName(mongoTokenClaim.getClaimName());
        tokenClaim.setClaimValue(mongoTokenClaim.getClaimValue());
        return tokenClaim;
    }

    private TokenClaimMongo convert(TokenClaim tokenClaim) {
        TokenClaimMongo mongoTokenClaim = new TokenClaimMongo();
        mongoTokenClaim.setTokenType(tokenClaim.getTokenType().toString());
        mongoTokenClaim.setClaimName(tokenClaim.getClaimName());
        mongoTokenClaim.setClaimValue(tokenClaim.getClaimValue());
        return mongoTokenClaim;
    }
}
