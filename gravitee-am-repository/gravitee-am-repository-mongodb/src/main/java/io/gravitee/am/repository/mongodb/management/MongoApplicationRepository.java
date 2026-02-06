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
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.CookieSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationAdvancedSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSAMLSettings;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.KeyType;
import io.gravitee.am.model.jose.OCTKey;
import io.gravitee.am.model.jose.OKPKey;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AccountSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.ApplicationAdvancedSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.ApplicationIdentityProviderMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.ApplicationMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.ApplicationOAuthSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.ApplicationSAMLSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.ApplicationScopeSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.ApplicationSecretSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.ApplicationSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.ClientSecretMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.CookieSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.JWKMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.LoginSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.MFASettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.PasswordSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.TokenClaimMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.risk.RiskAssessmentSettingsMongo;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.elemMatch;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.or;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_DOMAIN;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_NAME;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_UPDATED_AT;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoApplicationRepository extends AbstractManagementMongoRepository implements ApplicationRepository {

    private static final String FIELD_CLIENT_ID = "settings.oauth.clientId";
    private static final String FIELD_APPLICATION_IDENTITY_PROVIDERS = "identityProviders";
    private static final String FIELD_IDENTITY = "identity";
    // Kept for retro-compatibility
    private static final String FIELD_IDENTITIES = "identities";
    private static final String FIELD_FACTORS = "factors";
    private static final String FIELD_CERTIFICATE = "certificate";
    private static final String FIELD_GRANT_TYPES = "settings.oauth.grantTypes";
    private MongoCollection<ApplicationMongo> applicationsCollection;

    @PostConstruct
    public void init() {
        applicationsCollection = mongoOperations.getCollection("applications", ApplicationMongo.class);
        super.init(applicationsCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put( new Document(FIELD_DOMAIN, 1), new IndexOptions().name("d1"));
        indexes.put( new Document(FIELD_UPDATED_AT, -1), new IndexOptions().name("u_1"));
        indexes.put( new Document(FIELD_DOMAIN, 1).append(FIELD_CLIENT_ID, 1), new IndexOptions().name("d1soc1"));
        indexes.put( new Document(FIELD_DOMAIN, 1).append(FIELD_NAME, 1), new IndexOptions().name("d1n1"));
        indexes.put( new Document(FIELD_IDENTITIES, 1), new IndexOptions().name("i1"));
        indexes.put( new Document(FIELD_APPLICATION_IDENTITY_PROVIDERS + "." + FIELD_IDENTITY, 1), new IndexOptions().name("aidp1"));
        indexes.put( new Document(FIELD_CERTIFICATE, 1), new IndexOptions().name("c1"));
        indexes.put( new Document(FIELD_DOMAIN, 1).append(FIELD_GRANT_TYPES, 1), new IndexOptions().name("d1sog1"));

        super.createIndex(applicationsCollection, indexes);
    }

    @Override
    public Flowable<Application> findAll() {
        return Flowable.fromPublisher(applicationsCollection.find()).map(MongoApplicationRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<Application>> findAll(int page, int size) {
        Single<Long> countOperation = Observable.fromPublisher(applicationsCollection.countDocuments()).first(0l);
        Single<Set<Application>> applicationsOperation = Observable.fromPublisher(withMaxTime(applicationsCollection.find())
                .sort(new BasicDBObject(FIELD_UPDATED_AT, -1))
                .skip(size * page)
                .limit(size))
                .map(MongoApplicationRepository::convert).collect(HashSet::new, Set::add);
        return Single.zip(countOperation, applicationsOperation, (count, applications) -> new Page<>(applications, page, count))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Application> findByDomain(String domain) {
        return Flowable.fromPublisher(withMaxTime(applicationsCollection.find(eq(FIELD_DOMAIN, domain)))).map(MongoApplicationRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<Application>> findByDomain(String domain, int page, int size) {
        Bson query = eq(FIELD_DOMAIN, domain);
        return queryApplications(query, page, size).observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<Application>> findByDomain(String domain, List<String> applicationIds, int page, int size) {
        Bson query = and(eq(FIELD_DOMAIN, domain), in(FIELD_ID, applicationIds));
        return queryApplications(query, page, size).observeOn(Schedulers.computation());
    }

    private Single<Page<Application>> queryApplications(Bson query, int page, int size) {
        Single<Long> countOperation = Observable.fromPublisher(applicationsCollection
                        .countDocuments(query, countOptions()))
                .firstElement()
                .toSingle();
        Single<Set<Application>> applicationsOperation = Observable.fromPublisher(
                        withMaxTime(applicationsCollection.find(query))
                                .sort(new BasicDBObject(FIELD_UPDATED_AT, -1))
                                .skip(size * page).limit(size))
                .map(MongoApplicationRepository::convert)
                .collect(HashSet::new, Set::add);
        return Single.zip(countOperation, applicationsOperation, (count, applications) -> new Page<>(applications, page, count));
    }

    @Override
    public Single<Page<Application>> search(String domain, String query, int page, int size) {
        // currently search on client_id field
        Bson searchQuery = or(eq(FIELD_CLIENT_ID, query), eq(FIELD_NAME, query));
        // if query contains wildcard, use the regex query
        if (query.contains("*")) {
            // First escape regex metacharacters (except *) to prevent PatternSyntaxException
            String escapedQuery = escapeRegexMetacharacters(query);
            String compactQuery = escapedQuery.replaceAll("\\*+", ".*");
            String regex = "^" + compactQuery;
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            searchQuery = or(new BasicDBObject(FIELD_CLIENT_ID, pattern), new BasicDBObject(FIELD_NAME, pattern));
        }

        Bson mongoQuery = and(
                eq(FIELD_DOMAIN, domain),
                searchQuery);

        Single<Long> countOperation = Observable.fromPublisher(applicationsCollection.countDocuments(mongoQuery, countOptions())).first(0l);
        Single<Set<Application>> applicationsOperation = Observable.fromPublisher(withMaxTime(applicationsCollection.find(mongoQuery)).sort(new BasicDBObject(FIELD_UPDATED_AT, -1)).skip(size * page).limit(size)).map(MongoApplicationRepository::convert).collect(HashSet::new, Set::add);
        return Single.zip(countOperation, applicationsOperation, (count, applications) -> new Page<>(applications, page, count))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<Application>> search(String domain, List<String> applicationIds, String query, int page, int size) {
        // currently search on client_id field

        Bson searchQuery = and(in(FIELD_ID, applicationIds), or(eq(FIELD_CLIENT_ID, query), eq(FIELD_NAME, query)));
        // if query contains wildcard, use the regex query
        if (query.contains("*")) {
            // First escape regex metacharacters (except *) to prevent PatternSyntaxException
            String escapedQuery = escapeRegexMetacharacters(query);
            String compactQuery = escapedQuery.replaceAll("\\*+", ".*");
            String regex = "^" + compactQuery;
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            searchQuery = and(in(FIELD_ID, applicationIds), or(new BasicDBObject(FIELD_CLIENT_ID, pattern), new BasicDBObject(FIELD_NAME, pattern)));
        }

        Bson mongoQuery = and(
                eq(FIELD_DOMAIN, domain),
                searchQuery);

        Single<Long> countOperation = Observable.fromPublisher(applicationsCollection.countDocuments(mongoQuery, countOptions())).first(0l);
        Single<Set<Application>> applicationsOperation = Observable.fromPublisher(withMaxTime(applicationsCollection.find(mongoQuery)).sort(new BasicDBObject(FIELD_UPDATED_AT, -1)).skip(size * page).limit(size)).map(MongoApplicationRepository::convert).collect(HashSet::new, Set::add);
        return Single.zip(countOperation, applicationsOperation, (count, applications) -> new Page<>(applications, page, count))
                .observeOn(Schedulers.computation());
    }

    /**
     * Escapes regex metacharacters in a query string, except for the asterisk (*) which is
     * used as a wildcard. This prevents PatternSyntaxException when users search for
     * special characters like [ ] { } etc.
     *
     * @param query the search query that may contain regex metacharacters
     * @return the query with metacharacters escaped
     */
    private static String escapeRegexMetacharacters(String query) {
        // Escape all regex metacharacters except * (which we use as wildcard)
        // Metacharacters: . ^ $ | ? + \ [ ] { } ( )
        return query.replaceAll("([\\[\\]{}()^$.|?+\\\\])", "\\\\$1");
    }

    @Override
    public Flowable<Application> findByCertificate(String certificate) {
        return Flowable.fromPublisher(applicationsCollection.find(eq(FIELD_CERTIFICATE, certificate))).map(MongoApplicationRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Application> findByIdentityProvider(String identityProvider) {
        final BsonDocument bsonDocument = new BsonDocument(FIELD_IDENTITY, new BsonString(identityProvider));
        final Bson query = elemMatch(FIELD_APPLICATION_IDENTITY_PROVIDERS, Document.parse(bsonDocument.toJson()));
        return Flowable.fromPublisher(applicationsCollection.find(query)).map(MongoApplicationRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Application> findByFactor(String factor) {
        return Flowable.fromPublisher(applicationsCollection.find(eq(FIELD_FACTORS, factor))).map(MongoApplicationRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Application> findById(String id) {
        return Observable.fromPublisher(applicationsCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(MongoApplicationRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Application> findByDomainAndClientId(String domain, String clientId) {
        return Observable.fromPublisher(applicationsCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_CLIENT_ID, clientId)))
                .first()).firstElement().map(MongoApplicationRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Application> findByDomainAndExtensionGrant(String domain, String extensionGrant) {
        return Flowable.fromPublisher(applicationsCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_GRANT_TYPES, extensionGrant)))).map(MongoApplicationRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Application> findByIdIn(List<String> ids) {
        return Flowable.fromPublisher(withMaxTime(applicationsCollection.find(in(FIELD_ID, ids)))).map(MongoApplicationRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Application> create(Application item) {
        ApplicationMongo application = convert(item);
        application.setId(application.getId() == null ? RandomString.generate() : application.getId());
        return Single.fromPublisher(applicationsCollection.insertOne(application)).flatMap(success -> {
            item.setId(application.getId());
            return Single.just(item);
        })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Application> update(Application item) {
        ApplicationMongo application = convert(item);
        return Single.fromPublisher(applicationsCollection.replaceOne(eq(FIELD_ID, application.getId()), application))
                .flatMap(success -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(applicationsCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Long> count() {
        return Single.fromPublisher(applicationsCollection.countDocuments())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Long> countByDomain(String domain) {
        return Single.fromPublisher(applicationsCollection.countDocuments(eq(FIELD_DOMAIN, domain), countOptions()))
                .observeOn(Schedulers.computation());
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
        applicationMongo.setFactors(other.getFactors());
        applicationMongo.setCertificate(other.getCertificate());
        applicationMongo.setMetadata(other.getMetadata() != null ? new Document(other.getMetadata()) : null);
        applicationMongo.setSettings(convert(other.getSettings()));
        applicationMongo.setIdentityProviders(convert(other.getIdentityProviders()));
        applicationMongo.setIdentities(applicationMongo.getIdentityProviders().stream().map(ApplicationIdentityProviderMongo::getIdentity).collect(toSet()));
        applicationMongo.setCreatedAt(other.getCreatedAt());
        applicationMongo.setUpdatedAt(other.getUpdatedAt());
        applicationMongo.setSecretSettings(convertToSecretSettingsMongo(other.getSecretSettings()));
        applicationMongo.setSecrets(convertToClientSecretMongo(other.getSecrets()));

        return applicationMongo;
    }


    private static Application convert(ApplicationMongo other) {
        Application application = new Application();
        application.setId(other.getId());
        application.setName(other.getName());
        application.setType(other.getType() != null ? ApplicationType.valueOf(other.getType()) : null);
        application.setDescription(other.getDescription());
        application.setDomain(other.getDomain());
        application.setEnabled(other.isEnabled());
        application.setTemplate(other.isTemplate());
        application.setFactors(other.getFactors());
        application.setCertificate(other.getCertificate());
        application.setMetadata(other.getMetadata());
        application.setSettings(convert(other.getSettings()));
        application.setIdentityProviders(convert(other.getIdentityProviders()));
        application.setCreatedAt(other.getCreatedAt());
        application.setUpdatedAt(other.getUpdatedAt());
        application.setSecretSettings(convertToSecretSettings(other.getSecretSettings()));
        application.setSecrets(convertToClientSecret(other.getSecrets()));
        return application;
    }

    private static List<ClientSecretMongo> convertToClientSecretMongo(List<ClientSecret> clientSecrets) {
        List<ClientSecretMongo> result = null;
        if (clientSecrets != null) {
            result = clientSecrets.stream().map(secret -> {
                if (secret.getId() == null) {
                    secret.setId(UUID.randomUUID().toString());
                }

                ClientSecretMongo mongoSecret = new ClientSecretMongo();
                mongoSecret.setId(secret.getId());
                mongoSecret.setSecret(secret.getSecret());
                mongoSecret.setName(secret.getName());
                mongoSecret.setSettingsId(secret.getSettingsId());
                mongoSecret.setCreatedAt(secret.getCreatedAt());
                return mongoSecret;
            }).collect(Collectors.toList());
        }
        return result;
    }

    private static List<ClientSecret> convertToClientSecret(List<ClientSecretMongo> clientSecretMongos) {
        List<ClientSecret> result = null;
        if (clientSecretMongos != null) {
            result = clientSecretMongos.stream().map(mongoSecret -> {
                ClientSecret secret = new ClientSecret();
                secret.setId(mongoSecret.getId());
                secret.setSecret(mongoSecret.getSecret());
                secret.setName(mongoSecret.getName());
                secret.setSettingsId(mongoSecret.getSettingsId());
                secret.setCreatedAt(mongoSecret.getCreatedAt());
                return secret;
            }).collect(Collectors.toList());
        }
        return result;
    }

    private static List<ApplicationSecretSettings> convertToSecretSettings(List<ApplicationSecretSettingsMongo> secretSettingsMongo) {
        List<ApplicationSecretSettings> result = null;
        if (secretSettingsMongo != null) {
            result = secretSettingsMongo.stream().map(ssm -> {
                ApplicationSecretSettings secretSettings = new ApplicationSecretSettings();
                secretSettings.setId(ssm.getId());
                secretSettings.setAlgorithm(ssm.getAlgorithm());
                secretSettings.setProperties(ofNullable(ssm.getProperties()).map(TreeMap::new).orElse(null));
                return secretSettings;
            }).collect(Collectors.toList());
        }
        return result;
    }

    private static List<ApplicationSecretSettingsMongo> convertToSecretSettingsMongo(List<ApplicationSecretSettings> secretSettings) {
        List<ApplicationSecretSettingsMongo> result = null;
        if (secretSettings != null) {
            result = secretSettings.stream().map(ssm -> {
                ApplicationSecretSettingsMongo secretSettingsMongo = new ApplicationSecretSettingsMongo();
                secretSettingsMongo.setId(ssm.getId());
                secretSettingsMongo.setAlgorithm(ssm.getAlgorithm());
                secretSettingsMongo.setProperties(ofNullable(ssm.getProperties()).map(Document::new).orElse(null));
                return secretSettingsMongo;
            }).collect(Collectors.toList());
        }
        return result;
    }

    private static ApplicationSettingsMongo convert(ApplicationSettings other) {
        if (other == null) {
            return null;
        }

        ApplicationSettingsMongo applicationSettingsMongo = new ApplicationSettingsMongo();
        applicationSettingsMongo.setOauth(convert(other.getOauth()));
        applicationSettingsMongo.setSaml(convert(other.getSaml()));
        applicationSettingsMongo.setAccount(convert(other.getAccount()));
        applicationSettingsMongo.setLogin(convert(other.getLogin()));
        applicationSettingsMongo.setAdvanced(convert(other.getAdvanced()));
        applicationSettingsMongo.setPasswordSettings(convert(other.getPasswordSettings()));
        applicationSettingsMongo.setMfa(convert(other.getMfa()));
        applicationSettingsMongo.setCookieSettings(convert(other.getCookieSettings()));
        applicationSettingsMongo.setRiskAssessment(convert(other.getRiskAssessment()));
        return applicationSettingsMongo;
    }

    private static ApplicationSettings convert(ApplicationSettingsMongo other) {
        if (other == null) {
            return null;
        }

        ApplicationSettings applicationSettings = new ApplicationSettings();
        applicationSettings.setOauth(convert(other.getOauth()));
        applicationSettings.setSaml(convert(other.getSaml()));
        applicationSettings.setAccount(convert(other.getAccount()));
        applicationSettings.setLogin(convert(other.getLogin()));
        applicationSettings.setAdvanced(convert(other.getAdvanced()));
        applicationSettings.setPasswordSettings(convert(other.getPasswordSettings()));
        applicationSettings.setMfa(convert(other.getMfa()));
        applicationSettings.setCookieSettings(convert(other.getCookieSettings()));
        applicationSettings.setRiskAssessment(convert(other.getRiskAssessment()));
        return applicationSettings;
    }

    private static ApplicationOAuthSettingsMongo convert(ApplicationOAuthSettings other) {
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
        applicationOAuthSettingsMongo.setDefaultScopes(other.getDefaultScopes());
        applicationOAuthSettingsMongo.setScopeApprovals(other.getScopeApprovals() != null ? new Document(other.getScopeApprovals()) : null);
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
        applicationOAuthSettingsMongo.setTlsClientCertificateBoundAccessTokens(other.isTlsClientCertificateBoundAccessTokens());
        applicationOAuthSettingsMongo.setAuthorizationSignedResponseAlg(other.getAuthorizationSignedResponseAlg());
        applicationOAuthSettingsMongo.setAuthorizationEncryptedResponseAlg(other.getAuthorizationEncryptedResponseAlg());
        applicationOAuthSettingsMongo.setAuthorizationEncryptedResponseEnc(other.getAuthorizationEncryptedResponseEnc());
        applicationOAuthSettingsMongo.setForcePKCE(other.isForcePKCE());
        applicationOAuthSettingsMongo.setForceS256CodeChallengeMethod(other.isForceS256CodeChallengeMethod());
        applicationOAuthSettingsMongo.setPostLogoutRedirectUris(other.getPostLogoutRedirectUris());
        applicationOAuthSettingsMongo.setSingleSignOut(other.isSingleSignOut());
        applicationOAuthSettingsMongo.setSilentReAuthentication(other.isSilentReAuthentication());
        if (other.getScopeSettings() != null) {
            applicationOAuthSettingsMongo.setScopeSettings(other.getScopeSettings().stream().map(MongoApplicationRepository::convert).collect(Collectors.toList()));
        }
        applicationOAuthSettingsMongo.setRequireParRequest(other.isRequireParRequest());

        applicationOAuthSettingsMongo.setBackchannelAuthRequestSignAlg(other.getBackchannelAuthRequestSignAlg());
        applicationOAuthSettingsMongo.setBackchannelTokenDeliveryMode(other.getBackchannelTokenDeliveryMode());
        applicationOAuthSettingsMongo.setBackchannelUserCodeParameter(other.isBackchannelUserCodeParameter());
        applicationOAuthSettingsMongo.setBackchannelClientNotificationEndpoint(other.getBackchannelClientNotificationEndpoint());
        applicationOAuthSettingsMongo.setDisableRefreshTokenRotation(other.isDisableRefreshTokenRotation());

        return applicationOAuthSettingsMongo;
    }

    private static ApplicationOAuthSettings convert(ApplicationOAuthSettingsMongo other) {
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
        applicationOAuthSettings.setDefaultScopes(other.getDefaultScopes());
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
        applicationOAuthSettings.setTlsClientCertificateBoundAccessTokens(other.isTlsClientCertificateBoundAccessTokens());
        applicationOAuthSettings.setAuthorizationSignedResponseAlg(other.getAuthorizationSignedResponseAlg());
        applicationOAuthSettings.setAuthorizationEncryptedResponseAlg(other.getAuthorizationEncryptedResponseAlg());
        applicationOAuthSettings.setAuthorizationEncryptedResponseEnc(other.getAuthorizationEncryptedResponseEnc());
        applicationOAuthSettings.setForcePKCE(other.isForcePKCE());
        applicationOAuthSettings.setForceS256CodeChallengeMethod(other.isForceS256CodeChallengeMethod());
        applicationOAuthSettings.setPostLogoutRedirectUris(other.getPostLogoutRedirectUris());
        applicationOAuthSettings.setSingleSignOut(other.isSingleSignOut());
        applicationOAuthSettings.setSilentReAuthentication(other.isSilentReAuthentication());
        if (other.getScopeSettings() != null) {
            applicationOAuthSettings.setScopeSettings(other.getScopeSettings().stream().map(MongoApplicationRepository::convert).collect(Collectors.toList()));
        }

        applicationOAuthSettings.setBackchannelAuthRequestSignAlg(other.getBackchannelAuthRequestSignAlg());
        applicationOAuthSettings.setBackchannelTokenDeliveryMode(other.getBackchannelTokenDeliveryMode());
        applicationOAuthSettings.setBackchannelUserCodeParameter(other.isBackchannelUserCodeParameter());
        applicationOAuthSettings.setBackchannelClientNotificationEndpoint(other.getBackchannelClientNotificationEndpoint());
        applicationOAuthSettings.setRequireParRequest(other.isRequireParRequest());
        applicationOAuthSettings.setDisableRefreshTokenRotation(other.isDisableRefreshTokenRotation());

        return applicationOAuthSettings;
    }

    private static ApplicationSAMLSettingsMongo convert(ApplicationSAMLSettings other) {
        if (other == null) {
            return null;
        }
        ApplicationSAMLSettingsMongo applicationSAMLSettingsMongo = new ApplicationSAMLSettingsMongo();
        applicationSAMLSettingsMongo.setEntityId(other.getEntityId());
        applicationSAMLSettingsMongo.setAttributeConsumeServiceUrl(other.getAttributeConsumeServiceUrl());
        applicationSAMLSettingsMongo.setSingleLogoutServiceUrl(other.getSingleLogoutServiceUrl());
        applicationSAMLSettingsMongo.setCertificate(other.getCertificate());
        applicationSAMLSettingsMongo.setWantResponseSigned(other.isWantResponseSigned());
        applicationSAMLSettingsMongo.setWantAssertionsSigned(other.isWantAssertionsSigned());
        applicationSAMLSettingsMongo.setResponseBinding(other.getResponseBinding());
        return applicationSAMLSettingsMongo;
    }

    private static ApplicationSAMLSettings convert(ApplicationSAMLSettingsMongo other) {
        if (other == null) {
            return null;
        }
        ApplicationSAMLSettings applicationSAMLSettings = new ApplicationSAMLSettings();
        applicationSAMLSettings.setEntityId(other.getEntityId());
        applicationSAMLSettings.setAttributeConsumeServiceUrl(other.getAttributeConsumeServiceUrl());
        applicationSAMLSettings.setSingleLogoutServiceUrl(other.getSingleLogoutServiceUrl());
        applicationSAMLSettings.setCertificate(other.getCertificate());
        applicationSAMLSettings.setWantResponseSigned(other.isWantResponseSigned());
        applicationSAMLSettings.setWantAssertionsSigned(other.isWantAssertionsSigned());
        applicationSAMLSettings.setResponseBinding(other.getResponseBinding());
        return applicationSAMLSettings;
    }

    private static ApplicationScopeSettings convert(ApplicationScopeSettingsMongo other) {
        ApplicationScopeSettings applicationScopeSettings = new ApplicationScopeSettings();
        applicationScopeSettings.setScope(other.getScope());
        applicationScopeSettings.setScopeApproval(other.getScopeApproval());
        applicationScopeSettings.setDefaultScope(other.isDefaultScope());
        return applicationScopeSettings;
    }

    private static ApplicationScopeSettingsMongo convert(ApplicationScopeSettings other) {
        ApplicationScopeSettingsMongo applicationScopeSettingsMongo = new ApplicationScopeSettingsMongo();
        applicationScopeSettingsMongo.setScope(other.getScope());
        applicationScopeSettingsMongo.setScopeApproval(other.getScopeApproval());
        applicationScopeSettingsMongo.setDefaultScope(other.isDefaultScope());
        return applicationScopeSettingsMongo;
    }

    private static AccountSettings convert(AccountSettingsMongo accountSettingsMongo) {
        return accountSettingsMongo != null ? accountSettingsMongo.convert() : null;
    }

    private static AccountSettingsMongo convert(AccountSettings accountSettings) {
        return AccountSettingsMongo.convert(accountSettings);
    }

    private static LoginSettings convert(LoginSettingsMongo loginSettingsMongo) {
        return loginSettingsMongo != null ? loginSettingsMongo.convert() : null;
    }

    private static LoginSettingsMongo convert(LoginSettings loginSettings) {
        return LoginSettingsMongo.convert(loginSettings);
    }

    private static PasswordSettings convert(PasswordSettingsMongo passwordSettingsMongo) {
        return passwordSettingsMongo != null ? passwordSettingsMongo.convert() : null;
    }

    private static PasswordSettingsMongo convert(PasswordSettings passwordSettings) {
        return PasswordSettingsMongo.convert(passwordSettings);
    }

    private static ApplicationAdvancedSettings convert(ApplicationAdvancedSettingsMongo other) {
        if (other == null) {
            return null;
        }

        ApplicationAdvancedSettings applicationAdvancedSettings = new ApplicationAdvancedSettings();
        applicationAdvancedSettings.setSkipConsent(other.isSkipConsent());
        applicationAdvancedSettings.setFlowsInherited(other.isFlowsInherited());
        return applicationAdvancedSettings;
    }

    private static ApplicationAdvancedSettingsMongo convert(ApplicationAdvancedSettings other) {
        if (other == null) {
            return null;
        }

        ApplicationAdvancedSettingsMongo applicationAdvancedSettingsMongo = new ApplicationAdvancedSettingsMongo();
        applicationAdvancedSettingsMongo.setSkipConsent(other.isSkipConsent());
        applicationAdvancedSettingsMongo.setFlowsInherited(other.isFlowsInherited());
        return applicationAdvancedSettingsMongo;
    }

    private static MFASettings convert(MFASettingsMongo mfaSettingsMongo) {
        return mfaSettingsMongo != null ? mfaSettingsMongo.convert() : null;
    }

    private static MFASettingsMongo convert(MFASettings mfaSettings) {
        return MFASettingsMongo.convert(mfaSettings);
    }

    private static CookieSettingsMongo convert(CookieSettings cookieSettings) {
        return CookieSettingsMongo.convert(cookieSettings);
    }

    private static CookieSettings convert(CookieSettingsMongo cookieSettingsMongo) {
        return cookieSettingsMongo != null ? cookieSettingsMongo.convert() : null;
    }

    private static RiskAssessmentSettingsMongo convert(RiskAssessmentSettings riskAssessment) {
        return RiskAssessmentSettingsMongo.convert(riskAssessment);
    }

    private static RiskAssessmentSettings convert(RiskAssessmentSettingsMongo riskAssessment) {
        if (isNull(riskAssessment)) {
            return null;
        }
        return riskAssessment.convert();
    }

    private static List<TokenClaim> getTokenClaims(List<TokenClaimMongo> mongoTokenClaims) {
        if (mongoTokenClaims == null) {
            return null;
        }
        return mongoTokenClaims.stream().map(MongoApplicationRepository::convert).collect(Collectors.toList());
    }

    private static List<TokenClaimMongo> getMongoTokenClaims(List<TokenClaim> tokenClaims) {
        if (tokenClaims == null) {
            return null;
        }
        return tokenClaims.stream().map(MongoApplicationRepository::convert).collect(Collectors.toList());
    }

    private static TokenClaim convert(TokenClaimMongo mongoTokenClaim) {
        TokenClaim tokenClaim = new TokenClaim();
        tokenClaim.setTokenType(TokenTypeHint.from(mongoTokenClaim.getTokenType()));
        tokenClaim.setClaimName(mongoTokenClaim.getClaimName());
        tokenClaim.setClaimValue(mongoTokenClaim.getClaimValue());
        return tokenClaim;
    }

    private static TokenClaimMongo convert(TokenClaim tokenClaim) {
        TokenClaimMongo mongoTokenClaim = new TokenClaimMongo();
        mongoTokenClaim.setTokenType(tokenClaim.getTokenType().toString());
        mongoTokenClaim.setClaimName(tokenClaim.getClaimName());
        mongoTokenClaim.setClaimValue(tokenClaim.getClaimValue());
        return mongoTokenClaim;
    }

    private static JWKSet convert(List<JWKMongo> jwksMongo) {
        if (jwksMongo == null) {
            return null;
        }

        JWKSet jwkSet = new JWKSet();

        List<JWK> jwkList = jwksMongo.stream()
                .map(jwkMongo -> convert(jwkMongo))
                .collect(Collectors.toList());

        jwkSet.setKeys(jwkList);

        return jwkSet;
    }

    private static JWK convert(JWKMongo jwkMongo) {
        if (jwkMongo == null) {
            return null;
        }

        JWK result;

        switch (KeyType.parse(jwkMongo.getKty())) {
            // @formatter:off
            case EC:
                result = convertEC(jwkMongo);
                break;
            case RSA:
                result = convertRSA(jwkMongo);
                break;
            case OCT:
                result = convertOCT(jwkMongo);
                break;
            case OKP:
                result = convertOKP(jwkMongo);
                break;
            default:
                result = null;
                // @formatter:on
        }

        if (result != null) {
            result.setAlg(jwkMongo.getAlg());
            result.setKeyOps(jwkMongo.getKeyOps() != null ? jwkMongo.getKeyOps().stream().collect(toSet()) : null);
            result.setKid(jwkMongo.getKid());
            result.setKty(jwkMongo.getKty());
            result.setUse(jwkMongo.getUse());
            result.setX5c(jwkMongo.getX5c() != null ? jwkMongo.getX5c().stream().collect(toSet()) : null);
            result.setX5t(jwkMongo.getX5t());
            result.setX5tS256(jwkMongo.getX5tS256());
            result.setX5u(jwkMongo.getX5u());
        }

        return result;
    }

    private static RSAKey convertRSA(JWKMongo rsaKeyMongo) {
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

    private static ECKey convertEC(JWKMongo ecKeyMongo) {
        ECKey key = new ECKey();

        // Public key
        key.setCrv(ecKeyMongo.getCrv());
        key.setX(ecKeyMongo.getX());
        key.setY(ecKeyMongo.getY());

        // Private key
        key.setD(ecKeyMongo.getD());
        return key;
    }

    private static OCTKey convertOCT(JWKMongo ecKeyMongo) {
        OCTKey key = new OCTKey();
        key.setK(ecKeyMongo.getK());
        return key;
    }

    private static OKPKey convertOKP(JWKMongo ecKeyMongo) {
        OKPKey key = new OKPKey();
        key.setCrv(ecKeyMongo.getCrv());
        key.setX(ecKeyMongo.getX());

        // Private key
        key.setD(ecKeyMongo.getD());

        return key;
    }

    private static List<JWKMongo> convert(JWKSet jwkSet) {
        if (jwkSet == null) {
            return null;
        }

        return jwkSet.getKeys().stream()
                .map(jwk -> convert(jwk))
                .collect(Collectors.toList());
    }

    private static JWKMongo convert(JWK jwk) {
        if (jwk == null) {
            return null;
        }

        JWKMongo result;

        switch (KeyType.parse(jwk.getKty())) {
            // @formatter:off
            case EC:
                result = convert((ECKey) jwk);
                break;
            case RSA:
                result = convert((RSAKey) jwk);
                break;
            case OCT:
                result = convert((OCTKey) jwk);
                break;
            case OKP:
                result = convert((OKPKey) jwk);
                break;
            default:
                result = null;
                // @formatter:on
        }

        result.setAlg(jwk.getAlg());
        result.setKeyOps(jwk.getKeyOps() != null ? jwk.getKeyOps().stream().collect(Collectors.toList()) : null);
        result.setKid(jwk.getKid());
        result.setKty(jwk.getKty());
        result.setUse(jwk.getUse());
        result.setX5c(jwk.getX5c() != null ? jwk.getX5c().stream().collect(Collectors.toList()) : null);
        result.setX5t(jwk.getX5t());
        result.setX5tS256(jwk.getX5tS256());
        result.setX5u(jwk.getX5u());

        return result;
    }

    private static JWKMongo convert(RSAKey rsaKey) {
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

    private static JWKMongo convert(ECKey ecKey) {
        JWKMongo key = new JWKMongo();
        key.setCrv(ecKey.getCrv());
        key.setX(ecKey.getX());
        key.setY(ecKey.getY());
        key.setD(ecKey.getD());
        return key;
    }

    private static JWKMongo convert(OKPKey okpKey) {
        JWKMongo key = new JWKMongo();
        key.setCrv(okpKey.getCrv());
        key.setX(okpKey.getX());
        key.setD(okpKey.getD());
        return key;
    }

    private static JWKMongo convert(OCTKey octKey) {
        JWKMongo key = new JWKMongo();
        key.setK(octKey.getK());
        return key;
    }

    private static SortedSet<ApplicationIdentityProvider> convert(Set<ApplicationIdentityProviderMongo> applicationIdentityProvidersMongo) {
        return ofNullable(applicationIdentityProvidersMongo).orElse(Set.of()).stream()
                .map(MongoApplicationRepository::convert)
                .collect(toCollection(TreeSet::new));
    }

    private static ApplicationIdentityProvider convert(ApplicationIdentityProviderMongo idpSettingsMongo) {
        var identityProviderSettings = new ApplicationIdentityProvider();
        identityProviderSettings.setIdentity(idpSettingsMongo.getIdentity());
        identityProviderSettings.setSelectionRule(idpSettingsMongo.getSelectionRule());
        identityProviderSettings.setPriority(idpSettingsMongo.getPriority());
        return identityProviderSettings;
    }

    private static Set<ApplicationIdentityProviderMongo> convert(SortedSet<ApplicationIdentityProvider> applicationIdentityProviders) {
        return ofNullable(applicationIdentityProviders).orElse(new TreeSet<>()).stream()
                .map(MongoApplicationRepository::convert)
                .collect(toSet());
    }

    private static ApplicationIdentityProviderMongo convert(ApplicationIdentityProvider idpSettingsMongo) {
        var identityProviderSettings = new ApplicationIdentityProviderMongo();
        identityProviderSettings.setIdentity(idpSettingsMongo.getIdentity());
        identityProviderSettings.setSelectionRule(idpSettingsMongo.getSelectionRule());
        identityProviderSettings.setPriority(idpSettingsMongo.getPriority());
        return identityProviderSettings;
    }
}
