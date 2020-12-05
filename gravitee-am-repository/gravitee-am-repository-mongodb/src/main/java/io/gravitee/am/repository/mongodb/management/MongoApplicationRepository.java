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

import com.mongodb.BasicDBObject;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationAdvancedSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.jose.*;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.*;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoApplicationRepository extends AbstractManagementMongoRepository implements ApplicationRepository {

    private static final String FIELD_CLIENT_ID = "settings.oauth.clientId";
    private static final String FIELD_IDENTITIES = "identities";
    private static final String FIELD_FACTORS = "factors";
    private static final String FIELD_CERTIFICATE = "certificate";
    private static final String FIELD_GRANT_TYPES= "settings.oauth.grantTypes";
    private MongoCollection<ApplicationMongo> applicationsCollection;

    @PostConstruct
    public void init() {
        applicationsCollection = mongoOperations.getCollection("applications", ApplicationMongo.class);
        super.init(applicationsCollection);
        super.createIndex(applicationsCollection, new Document(FIELD_DOMAIN, 1));
        super.createIndex(applicationsCollection, new Document(FIELD_UPDATED_AT, -1));
        super.createIndex(applicationsCollection, new Document(FIELD_DOMAIN, 1).append(FIELD_CLIENT_ID, 1));
        super.createIndex(applicationsCollection, new Document(FIELD_DOMAIN, 1).append(FIELD_NAME, 1));
        super.createIndex(applicationsCollection, new Document(FIELD_IDENTITIES, 1));
        super.createIndex(applicationsCollection, new Document(FIELD_CERTIFICATE, 1));
        super.createIndex(applicationsCollection, new Document(FIELD_DOMAIN, 1).append(FIELD_GRANT_TYPES, 1));
    }

    @Override
    public Single<List<Application>> findAll() {
        return Observable.fromPublisher(applicationsCollection.find()).map(this::convert).collect(ArrayList::new, List::add);
    }

    @Override
    public Single<Page<Application>> findAll(int page, int size) {
        Single<Long> countOperation = Observable.fromPublisher(applicationsCollection.countDocuments()).first(0l);
        Single<Set<Application>> applicationsOperation = Observable.fromPublisher(applicationsCollection.find().sort(new BasicDBObject(FIELD_UPDATED_AT, -1)).skip(size * page).limit(size)).map(this::convert).collect(HashSet::new, Set::add);
        return Single.zip(countOperation, applicationsOperation, (count, applications) -> new Page<>(applications, page, count));
    }

    @Override
    public Single<Page<Application>> findByDomain(String domain, int page, int size) {
        Single<Long> countOperation = Observable.fromPublisher(applicationsCollection.countDocuments(eq(FIELD_DOMAIN, domain))).first(0l);
        Single<Set<Application>> applicationsOperation = Observable.fromPublisher(applicationsCollection.find(eq(FIELD_DOMAIN, domain)).sort(new BasicDBObject(FIELD_UPDATED_AT, -1)).skip(size * page).limit(size)).map(this::convert).collect(HashSet::new, Set::add);
        return Single.zip(countOperation, applicationsOperation, (count, applications) -> new Page<>(applications, page, count));
    }

    @Override
    public Single<Page<Application>> search(String domain, String query, int page, int size) {
        // currently search on client_id field
        Bson searchQuery = or(eq(FIELD_CLIENT_ID, query), eq(FIELD_NAME, query));
        // if query contains wildcard, use the regex query
        if (query.contains("*")) {
            String compactQuery = query.replaceAll("\\*+", ".*");
            String regex = "^" + compactQuery;
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            searchQuery = or(new BasicDBObject(FIELD_CLIENT_ID, pattern), new BasicDBObject(FIELD_NAME, pattern));
        }

        Bson mongoQuery = and(
                eq(FIELD_DOMAIN, domain),
                searchQuery);

        Single<Long> countOperation = Observable.fromPublisher(applicationsCollection.countDocuments(mongoQuery)).first(0l);
        Single<Set<Application>> applicationsOperation = Observable.fromPublisher(applicationsCollection.find(mongoQuery).sort(new BasicDBObject(FIELD_UPDATED_AT, -1)).skip(size * page).limit(size)).map(this::convert).collect(HashSet::new, Set::add);
        return Single.zip(countOperation, applicationsOperation, (count, applications) -> new Page<>(applications, page, count));
    }

    @Override
    public Single<Set<Application>> findByCertificate(String certificate) {
        return Observable.fromPublisher(applicationsCollection.find(eq(FIELD_CERTIFICATE, certificate))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Set<Application>> findByIdentityProvider(String identityProvider) {
        return Observable.fromPublisher(applicationsCollection.find(eq(FIELD_IDENTITIES, identityProvider))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Set<Application>> findByFactor(String factor) {
        return Observable.fromPublisher(applicationsCollection.find(eq(FIELD_FACTORS, factor))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Maybe<Application> findById(String id) {
        return Observable.fromPublisher(applicationsCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Maybe<Application> findByDomainAndClientId(String domain, String clientId) {
        return Observable.fromPublisher(applicationsCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_CLIENT_ID, clientId)))
                .first()).firstElement().map(this::convert);
    }

    @Override
    public Single<Set<Application>> findByDomainAndExtensionGrant(String domain, String extensionGrant) {
        return Observable.fromPublisher(applicationsCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_GRANT_TYPES, extensionGrant)))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Set<Application>> findByIdIn(List<String> ids) {
        return Observable.fromPublisher(applicationsCollection.find(in(FIELD_ID, ids))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Application> create(Application item) {
        ApplicationMongo application = convert(item);
        application.setId(application.getId() == null ? RandomString.generate() : application.getId());
        return Single.fromPublisher(applicationsCollection.insertOne(application)).flatMap(success -> findById(application.getId()).toSingle());
    }

    @Override
    public Single<Application> update(Application item) {
        ApplicationMongo application = convert(item);
        return Single.fromPublisher(applicationsCollection.replaceOne(eq(FIELD_ID, application.getId()), application)).flatMap(success -> findById(application.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(applicationsCollection.deleteOne(eq(FIELD_ID, id)));
    }

    @Override
    public Single<Long> count() {
        return Single.fromPublisher(applicationsCollection.countDocuments());
    }

    @Override
    public Single<Long> countByDomain(String domain) {
        return Single.fromPublisher(applicationsCollection.countDocuments(eq(FIELD_DOMAIN, domain)));
    }

    private ApplicationMongo convert(Application other) {
        ApplicationMongo applicationMongo = new ApplicationMongo();
        applicationMongo.setId(other.getId());
        applicationMongo.setName(other.getName());
        applicationMongo.setType(other.getType() != null ? other.getType().toString() : null);
        applicationMongo.setDescription(other.getDescription());
        applicationMongo.setDomain(other.getDomain());
        applicationMongo.setEnabled(other.isEnabled());
        applicationMongo.setTemplate(other.isTemplate());
        applicationMongo.setIdentities(other.getIdentities());
        applicationMongo.setFactors(other.getFactors());
        applicationMongo.setCertificate(other.getCertificate());
        applicationMongo.setMetadata(other.getMetadata() != null ? new Document(other.getMetadata()) : null);
        applicationMongo.setSettings(convert(other.getSettings()));
        applicationMongo.setCreatedAt(other.getCreatedAt());
        applicationMongo.setUpdatedAt(other.getUpdatedAt());

        return applicationMongo;
    }

    private Application convert(ApplicationMongo other) {
        Application application = new Application();
        application.setId(other.getId());
        application.setName(other.getName());
        application.setType(other.getType() != null ? ApplicationType.valueOf(other.getType()) : null);
        application.setDescription(other.getDescription());
        application.setDomain(other.getDomain());
        application.setEnabled(other.isEnabled());
        application.setTemplate(other.isTemplate());
        application.setIdentities(other.getIdentities());
        application.setFactors(other.getFactors());
        application.setCertificate(other.getCertificate());
        application.setMetadata(other.getMetadata());
        application.setSettings(convert(other.getSettings()));
        application.setCreatedAt(other.getCreatedAt());
        application.setUpdatedAt(other.getUpdatedAt());

        return application;
    }

    private ApplicationSettingsMongo convert(ApplicationSettings other) {
        if (other == null) {
            return null;
        }

        ApplicationSettingsMongo applicationSettingsMongo = new ApplicationSettingsMongo();
        applicationSettingsMongo.setOauth(convert(other.getOauth()));
        applicationSettingsMongo.setAccount(convert(other.getAccount()));
        applicationSettingsMongo.setLogin(convert(other.getLogin()));
        applicationSettingsMongo.setAdvanced(convert(other.getAdvanced()));
        return applicationSettingsMongo;
    }

    private ApplicationSettings convert(ApplicationSettingsMongo other) {
        if (other == null) {
            return null;
        }

        ApplicationSettings applicationSettings = new ApplicationSettings();
        applicationSettings.setOauth(convert(other.getOauth()));
        applicationSettings.setAccount(convert(other.getAccount()));
        applicationSettings.setLogin(convert(other.getLogin()));
        applicationSettings.setAdvanced(convert(other.getAdvanced()));
        return applicationSettings;
    }

    private ApplicationOAuthSettingsMongo convert(ApplicationOAuthSettings other) {
        if (other == null) {
            return null;
        }
        ApplicationOAuthSettingsMongo applicationOAuthSettingsMongo = new ApplicationOAuthSettingsMongo();
        applicationOAuthSettingsMongo.setClientId(other.getClientId());
        applicationOAuthSettingsMongo.setClientSecret(other.getClientSecret());
        applicationOAuthSettingsMongo.setClientType(other.getClientType());
        applicationOAuthSettingsMongo.setRedirectUris(other.getRedirectUris());
        applicationOAuthSettingsMongo.setResponseTypes(other.getResponseTypes());
        applicationOAuthSettingsMongo.setGrantTypes(other.getGrantTypes());
        applicationOAuthSettingsMongo.setApplicationType(other.getApplicationType());
        applicationOAuthSettingsMongo.setContacts(other.getContacts());
        applicationOAuthSettingsMongo.setClientName(other.getClientName());
        applicationOAuthSettingsMongo.setLogoUri(other.getLogoUri());
        applicationOAuthSettingsMongo.setClientUri(other.getClientUri());
        applicationOAuthSettingsMongo.setPolicyUri(other.getPolicyUri());
        applicationOAuthSettingsMongo.setTosUri(other.getTosUri());
        applicationOAuthSettingsMongo.setJwksUri(other.getJwksUri());
        applicationOAuthSettingsMongo.setJwks(convert(other.getJwks()));
        applicationOAuthSettingsMongo.setSectorIdentifierUri(other.getSectorIdentifierUri());
        applicationOAuthSettingsMongo.setSubjectType(other.getSubjectType());
        applicationOAuthSettingsMongo.setIdTokenSignedResponseAlg(other.getIdTokenSignedResponseAlg());
        applicationOAuthSettingsMongo.setIdTokenEncryptedResponseAlg(other.getIdTokenEncryptedResponseAlg());
        applicationOAuthSettingsMongo.setIdTokenEncryptedResponseEnc(other.getIdTokenEncryptedResponseEnc());
        applicationOAuthSettingsMongo.setUserinfoSignedResponseAlg(other.getUserinfoSignedResponseAlg());
        applicationOAuthSettingsMongo.setUserinfoEncryptedResponseAlg(other.getUserinfoEncryptedResponseAlg());
        applicationOAuthSettingsMongo.setUserinfoEncryptedResponseEnc(other.getUserinfoEncryptedResponseEnc());
        applicationOAuthSettingsMongo.setRequestObjectSigningAlg(other.getRequestObjectSigningAlg());
        applicationOAuthSettingsMongo.setRequestObjectEncryptionAlg(other.getRequestObjectEncryptionAlg());
        applicationOAuthSettingsMongo.setRequestObjectEncryptionEnc(other.getRequestObjectEncryptionEnc());
        applicationOAuthSettingsMongo.setTokenEndpointAuthMethod(other.getTokenEndpointAuthMethod());
        applicationOAuthSettingsMongo.setTokenEndpointAuthSigningAlg(other.getTokenEndpointAuthSigningAlg());
        applicationOAuthSettingsMongo.setDefaultMaxAge(other.getDefaultMaxAge());
        applicationOAuthSettingsMongo.setRequireAuthTime(other.getRequireAuthTime());
        applicationOAuthSettingsMongo.setDefaultACRvalues(other.getDefaultACRvalues());
        applicationOAuthSettingsMongo.setInitiateLoginUri(other.getInitiateLoginUri());
        applicationOAuthSettingsMongo.setRequestUris(other.getRequestUris());
        applicationOAuthSettingsMongo.setSoftwareId(other.getSoftwareId());
        applicationOAuthSettingsMongo.setSoftwareVersion(other.getSoftwareVersion());
        applicationOAuthSettingsMongo.setSoftwareStatement(other.getSoftwareStatement());
        applicationOAuthSettingsMongo.setRegistrationAccessToken(other.getRegistrationAccessToken());
        applicationOAuthSettingsMongo.setRegistrationClientUri(other.getRegistrationClientUri());
        applicationOAuthSettingsMongo.setClientIdIssuedAt(other.getClientIdIssuedAt());
        applicationOAuthSettingsMongo.setClientSecretExpiresAt(other.getClientSecretExpiresAt());
        applicationOAuthSettingsMongo.setScopes(other.getScopes());
        applicationOAuthSettingsMongo.setScopeApprovals(other.getScopeApprovals() != null ? new Document((Map)other.getScopeApprovals()) : null);
        applicationOAuthSettingsMongo.setEnhanceScopesWithUserPermissions(other.isEnhanceScopesWithUserPermissions());
        applicationOAuthSettingsMongo.setAccessTokenValiditySeconds(other.getAccessTokenValiditySeconds());
        applicationOAuthSettingsMongo.setRefreshTokenValiditySeconds(other.getRefreshTokenValiditySeconds());
        applicationOAuthSettingsMongo.setIdTokenValiditySeconds(other.getIdTokenValiditySeconds());
        applicationOAuthSettingsMongo.setTokenCustomClaims(getMongoTokenClaims(other.getTokenCustomClaims()));
        applicationOAuthSettingsMongo.setTlsClientAuthSubjectDn(other.getTlsClientAuthSubjectDn());
        applicationOAuthSettingsMongo.setTlsClientAuthSanDns(other.getTlsClientAuthSanDns());
        applicationOAuthSettingsMongo.setTlsClientAuthSanEmail(other.getTlsClientAuthSanEmail());
        applicationOAuthSettingsMongo.setTlsClientAuthSanIp(other.getTlsClientAuthSanIp());
        applicationOAuthSettingsMongo.setTlsClientAuthSanUri(other.getTlsClientAuthSanUri());
        applicationOAuthSettingsMongo.setAuthorizationSignedResponseAlg(other.getAuthorizationSignedResponseAlg());
        applicationOAuthSettingsMongo.setAuthorizationEncryptedResponseAlg(other.getAuthorizationEncryptedResponseAlg());
        applicationOAuthSettingsMongo.setAuthorizationEncryptedResponseEnc(other.getAuthorizationEncryptedResponseEnc());
        applicationOAuthSettingsMongo.setForcePKCE(other.isForcePKCE());
        return applicationOAuthSettingsMongo;
    }

    private ApplicationOAuthSettings convert(ApplicationOAuthSettingsMongo other) {
        if (other == null) {
            return null;
        }
        ApplicationOAuthSettings applicationOAuthSettings = new ApplicationOAuthSettings();
        applicationOAuthSettings.setClientId(other.getClientId());
        applicationOAuthSettings.setClientSecret(other.getClientSecret());
        applicationOAuthSettings.setClientType(other.getClientType());
        applicationOAuthSettings.setRedirectUris(other.getRedirectUris());
        applicationOAuthSettings.setResponseTypes(other.getResponseTypes());
        applicationOAuthSettings.setGrantTypes(other.getGrantTypes());
        applicationOAuthSettings.setApplicationType(other.getApplicationType());
        applicationOAuthSettings.setContacts(other.getContacts());
        applicationOAuthSettings.setClientName(other.getClientName());
        applicationOAuthSettings.setLogoUri(other.getLogoUri());
        applicationOAuthSettings.setClientUri(other.getClientUri());
        applicationOAuthSettings.setPolicyUri(other.getPolicyUri());
        applicationOAuthSettings.setTosUri(other.getTosUri());
        applicationOAuthSettings.setJwksUri(other.getJwksUri());
        applicationOAuthSettings.setJwks(convert(other.getJwks()));
        applicationOAuthSettings.setSectorIdentifierUri(other.getSectorIdentifierUri());
        applicationOAuthSettings.setSubjectType(other.getSubjectType());
        applicationOAuthSettings.setIdTokenSignedResponseAlg(other.getIdTokenSignedResponseAlg());
        applicationOAuthSettings.setIdTokenEncryptedResponseAlg(other.getIdTokenEncryptedResponseAlg());
        applicationOAuthSettings.setIdTokenEncryptedResponseEnc(other.getIdTokenEncryptedResponseEnc());
        applicationOAuthSettings.setUserinfoSignedResponseAlg(other.getUserinfoSignedResponseAlg());
        applicationOAuthSettings.setUserinfoEncryptedResponseAlg(other.getUserinfoEncryptedResponseAlg());
        applicationOAuthSettings.setUserinfoEncryptedResponseEnc(other.getUserinfoEncryptedResponseEnc());
        applicationOAuthSettings.setRequestObjectSigningAlg(other.getRequestObjectSigningAlg());
        applicationOAuthSettings.setRequestObjectEncryptionAlg(other.getRequestObjectEncryptionAlg());
        applicationOAuthSettings.setRequestObjectEncryptionEnc(other.getRequestObjectEncryptionEnc());
        applicationOAuthSettings.setTokenEndpointAuthMethod(other.getTokenEndpointAuthMethod());
        applicationOAuthSettings.setTokenEndpointAuthSigningAlg(other.getTokenEndpointAuthSigningAlg());
        applicationOAuthSettings.setDefaultMaxAge(other.getDefaultMaxAge());
        applicationOAuthSettings.setRequireAuthTime(other.getRequireAuthTime());
        applicationOAuthSettings.setDefaultACRvalues(other.getDefaultACRvalues());
        applicationOAuthSettings.setInitiateLoginUri(other.getInitiateLoginUri());
        applicationOAuthSettings.setRequestUris(other.getRequestUris());
        applicationOAuthSettings.setSoftwareId(other.getSoftwareId());
        applicationOAuthSettings.setSoftwareVersion(other.getSoftwareVersion());
        applicationOAuthSettings.setSoftwareStatement(other.getSoftwareStatement());
        applicationOAuthSettings.setRegistrationAccessToken(other.getRegistrationAccessToken());
        applicationOAuthSettings.setRegistrationClientUri(other.getRegistrationClientUri());
        applicationOAuthSettings.setClientIdIssuedAt(other.getClientIdIssuedAt());
        applicationOAuthSettings.setClientSecretExpiresAt(other.getClientSecretExpiresAt());
        applicationOAuthSettings.setScopes(other.getScopes());
        applicationOAuthSettings.setScopeApprovals(other.getScopeApprovals() != null ? (Map) other.getScopeApprovals() : null);
        applicationOAuthSettings.setEnhanceScopesWithUserPermissions(other.isEnhanceScopesWithUserPermissions());
        applicationOAuthSettings.setAccessTokenValiditySeconds(other.getAccessTokenValiditySeconds());
        applicationOAuthSettings.setRefreshTokenValiditySeconds(other.getRefreshTokenValiditySeconds());
        applicationOAuthSettings.setIdTokenValiditySeconds(other.getIdTokenValiditySeconds());
        applicationOAuthSettings.setTokenCustomClaims(getTokenClaims(other.getTokenCustomClaims()));
        applicationOAuthSettings.setTlsClientAuthSubjectDn(other.getTlsClientAuthSubjectDn());
        applicationOAuthSettings.setTlsClientAuthSanDns(other.getTlsClientAuthSanDns());
        applicationOAuthSettings.setTlsClientAuthSanEmail(other.getTlsClientAuthSanEmail());
        applicationOAuthSettings.setTlsClientAuthSanIp(other.getTlsClientAuthSanIp());
        applicationOAuthSettings.setTlsClientAuthSanUri(other.getTlsClientAuthSanUri());
        applicationOAuthSettings.setAuthorizationSignedResponseAlg(other.getAuthorizationSignedResponseAlg());
        applicationOAuthSettings.setAuthorizationEncryptedResponseAlg(other.getAuthorizationEncryptedResponseAlg());
        applicationOAuthSettings.setAuthorizationEncryptedResponseEnc(other.getAuthorizationEncryptedResponseEnc());
        applicationOAuthSettings.setForcePKCE(other.isForcePKCE());

        return applicationOAuthSettings;
    }

    private AccountSettings convert(AccountSettingsMongo accountSettingsMongo) {
        return accountSettingsMongo != null ? accountSettingsMongo.convert() : null;
    }

    private AccountSettingsMongo convert(AccountSettings accountSettings) {
        return AccountSettingsMongo.convert(accountSettings);
    }

    private LoginSettings convert(LoginSettingsMongo loginSettingsMongo) {
        return loginSettingsMongo != null ? loginSettingsMongo.convert() : null;
    }

    private LoginSettingsMongo convert(LoginSettings loginSettings) {
        return LoginSettingsMongo.convert(loginSettings);
    }

    private ApplicationAdvancedSettings convert(ApplicationAdvancedSettingsMongo other) {
        if (other == null) {
            return null;
        }

        ApplicationAdvancedSettings applicationAdvancedSettings = new ApplicationAdvancedSettings();
        applicationAdvancedSettings.setSkipConsent(other.isSkipConsent());
        return applicationAdvancedSettings;
    }

    private ApplicationAdvancedSettingsMongo convert(ApplicationAdvancedSettings other) {
        if (other == null) {
            return null;
        }

        ApplicationAdvancedSettingsMongo applicationAdvancedSettingsMongo = new ApplicationAdvancedSettingsMongo();
        applicationAdvancedSettingsMongo.setSkipConsent(other.isSkipConsent());
        return applicationAdvancedSettingsMongo;
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

        // Public key
        key.setE(rsaKeyMongo.getE());
        key.setN(rsaKeyMongo.getN());

        // Private key
        key.setD(rsaKeyMongo.getD());
        key.setP(rsaKeyMongo.getP());
        key.setQ(rsaKeyMongo.getQ());
        key.setDp(rsaKeyMongo.getDp());
        key.setDq(rsaKeyMongo.getDq());
        key.setQi(rsaKeyMongo.getQi());
        return key;
    }

    private ECKey convertEC(JWKMongo ecKeyMongo) {
        ECKey key = new ECKey();

        // Public key
        key.setCrv(ecKeyMongo.getCrv());
        key.setX(ecKeyMongo.getX());
        key.setY(ecKeyMongo.getY());

        // Private key
        key.setD(ecKeyMongo.getD());
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

        // Private key
        key.setD(ecKeyMongo.getD());

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
        key.setD(rsaKey.getD());
        key.setP(rsaKey.getP());
        key.setQ(rsaKey.getQ());
        key.setDp(rsaKey.getDp());
        key.setDq(rsaKey.getDq());
        key.setQi(rsaKey.getQi());
        return key;
    }

    private JWKMongo convert(ECKey ecKey) {
        JWKMongo key = new JWKMongo();
        key.setCrv(ecKey.getCrv());
        key.setX(ecKey.getX());
        key.setY(ecKey.getY());
        key.setD(ecKey.getD());
        return key;
    }

    private JWKMongo convert(OKPKey okpKey) {
        JWKMongo key = new JWKMongo();
        key.setCrv(okpKey.getCrv());
        key.setX(okpKey.getX());
        key.setD(okpKey.getD());
        return key;
    }

    private JWKMongo convert(OCTKey octKey) {
        JWKMongo key = new JWKMongo();
        key.setK(octKey.getK());
        return key;
    }
}
