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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.webauthn.AttestationConveyancePreference;
import io.gravitee.am.common.webauthn.AuthenticatorAttachment;
import io.gravitee.am.common.webauthn.UserVerification;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.SAMLSettings;
import io.gravitee.am.model.SelfServiceAccountManagementSettings;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.oidc.CIBASettingNotifier;
import io.gravitee.am.model.oidc.CIBASettings;
import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.SecurityProfileSettings;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.model.uma.UMASettings;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.search.DomainCriteria;
import io.gravitee.am.repository.mongodb.management.internal.model.AccountSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.DomainMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.LoginSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.PasswordSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.SAMLSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.SCIMSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.SelfServiceAccountManagementSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.WebAuthnSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.CIBASettingNotifierMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.CIBASettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.ClientRegistrationSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.OIDCSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.SecurityProfileSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.uma.UMASettingsMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoDomainRepository extends AbstractManagementMongoRepository implements DomainRepository {

    private MongoCollection<DomainMongo> domainsCollection;
    private static final String FIELD_HRID = "hrid";

    @PostConstruct
    public void init() {
        domainsCollection = mongoOperations.getCollection("domains", DomainMongo.class);
        super.init(domainsCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1), new IndexOptions().name("ri1rt1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_HRID, 1), new IndexOptions().name("ri1rt1h1"));

        super.createIndex(domainsCollection, indexes);
    }

    @Override
    public Flowable<Domain> findAll() {
        return Flowable.fromPublisher(withMaxTime(domainsCollection.find())).map(MongoDomainRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Domain> findById(String id) {
        return Observable.fromPublisher(domainsCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(MongoDomainRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Domain> findByHrid(ReferenceType referenceType, String referenceId, String hrid) {
        return Observable.fromPublisher(
                domainsCollection.find(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_HRID, hrid)
                        )
                )).firstElement().map(MongoDomainRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Domain> findByIdIn(Collection<String> ids) {
        return Flowable.fromPublisher(withMaxTime(domainsCollection.find(in(FIELD_ID, ids)))).map(MongoDomainRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Domain> findAllByReferenceId(String environmentId) {
        Bson mongoQuery = and(
                eq(FIELD_REFERENCE_TYPE, ReferenceType.ENVIRONMENT.name()),
                eq(FIELD_REFERENCE_ID, environmentId));
        return Flowable.fromPublisher(withMaxTime(domainsCollection.find(mongoQuery))).map(MongoDomainRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Domain> search(String environmentId, String query) {
        // currently search on hrid field
        Bson searchQuery = eq(FIELD_HRID, query);
        // if query contains wildcard, use the regex query
        if (query.contains("*")) {
            String compactQuery = query.replaceAll("\\*+", ".*");
            String regex = "^" + compactQuery;
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            searchQuery = new BasicDBObject(FIELD_HRID, pattern);
        }

        Bson mongoQuery = and(
                eq(FIELD_REFERENCE_TYPE, ReferenceType.ENVIRONMENT.name()),
                eq(FIELD_REFERENCE_ID, environmentId), searchQuery);

        return Flowable.fromPublisher(withMaxTime(domainsCollection.find(mongoQuery))).map(MongoDomainRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Domain> findAllByCriteria(DomainCriteria criteria) {

        Bson eqAlertEnabled = toBsonFilter("alertEnabled", criteria.isAlertEnabled());

        return toBsonFilter(criteria.isLogicalOR(), eqAlertEnabled)
                .switchIfEmpty(Single.just(new BsonDocument()))
                .flatMapPublisher(filter -> Flowable.fromPublisher(withMaxTime(domainsCollection.find(filter)))).map(MongoDomainRepository::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Domain> create(Domain item) {
        DomainMongo domain = convert(item);
        domain.setId(domain.getId() == null ? RandomString.generate() : domain.getId());
        return Single.fromPublisher(domainsCollection.insertOne(domain)).flatMap(success -> { item.setId(domain.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Domain> update(Domain item) {
        DomainMongo domain = convert(item);
        return Single.fromPublisher(domainsCollection.replaceOne(eq(FIELD_ID, domain.getId()), domain)).flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(domainsCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private static Domain convert(DomainMongo domainMongo) {
        if (domainMongo == null) {
            return null;
        }

        Domain domain = new Domain();
        domain.setId(domainMongo.getId());
        domain.setVersion(domainMongo.getVersion());
        domain.setHrid(domainMongo.getHrid());
        domain.setPath(domainMongo.getPath());
        domain.setVhostMode(domainMongo.isVhostMode());
        domain.setVhosts(domainMongo.getVhosts());
        domain.setCreatedAt(domainMongo.getCreatedAt());
        domain.setUpdatedAt(domainMongo.getUpdatedAt());
        domain.setName(domainMongo.getName());
        domain.setDescription(domainMongo.getDescription());
        domain.setEnabled(domainMongo.isEnabled());
        domain.setAlertEnabled(domainMongo.isAlertEnabled());
        domain.setOidc(convert(domainMongo.getOidc()));
        domain.setUma(convert(domainMongo.getUma()));
        domain.setScim(convert(domainMongo.getScim()));
        domain.setLoginSettings(convert(domainMongo.getLoginSettings()));
        domain.setWebAuthnSettings(convert(domainMongo.getWebAuthnSettings()));
        domain.setAccountSettings(convert(domainMongo.getAccountSettings()));
        domain.setPasswordSettings(convert(domainMongo.getPasswordSettings()));
        domain.setSelfServiceAccountManagementSettings(convert(domainMongo.getSelfServiceAccountManagementSettings()));
        domain.setSaml(convert(domainMongo.getSaml()));
        domain.setTags(domainMongo.getTags());
        domain.setReferenceType(domainMongo.getReferenceType());
        domain.setReferenceId(domainMongo.getReferenceId());
        domain.setIdentities(domainMongo.getIdentities());
        domain.setMaster(domainMongo.isMaster());
        domain.setCorsSettings(domainMongo.getCorsSettings());

        return domain;
    }

    private static DomainMongo convert(Domain domain) {
        if (domain == null) {
            return null;
        }

        DomainMongo domainMongo = new DomainMongo();
        domainMongo.setId(domain.getId());
        domainMongo.setVersion(domain.getVersion());
        domainMongo.setHrid(domain.getHrid());
        domainMongo.setPath(domain.getPath());
        domainMongo.setVhostMode(domain.isVhostMode());
        domainMongo.setVhosts(domain.getVhosts());
        domainMongo.setCreatedAt(domain.getCreatedAt());
        domainMongo.setUpdatedAt(domain.getUpdatedAt());
        domainMongo.setName(domain.getName());
        domainMongo.setDescription(domain.getDescription());
        domainMongo.setEnabled(domain.isEnabled());
        domainMongo.setAlertEnabled(domain.isAlertEnabled());
        domainMongo.setOidc(convert(domain.getOidc()));
        domainMongo.setUma(convert(domain.getUma()));
        domainMongo.setScim(convert(domain.getScim()));
        domainMongo.setLoginSettings(convert(domain.getLoginSettings()));
        domainMongo.setWebAuthnSettings(convert(domain.getWebAuthnSettings()));
        domainMongo.setAccountSettings(convert(domain.getAccountSettings()));
        domainMongo.setPasswordSettings(convert(domain.getPasswordSettings()));
        domainMongo.setSelfServiceAccountManagementSettings(convert(domain.getSelfServiceAccountManagementSettings()));
        domainMongo.setSaml(convert(domain.getSaml()));
        domainMongo.setTags(domain.getTags());
        domainMongo.setReferenceType(domain.getReferenceType());
        domainMongo.setReferenceId(domain.getReferenceId());
        domainMongo.setIdentities(domain.getIdentities());
        domainMongo.setMaster(domain.isMaster());
        domainMongo.setCorsSettings(domain.getCorsSettings());

        return domainMongo;
    }

    private static OIDCSettings convert(OIDCSettingsMongo oidcMongo) {
        if (oidcMongo == null) {
            return null;
        }

        OIDCSettings oidcSettings = new OIDCSettings();
        oidcSettings.setRedirectUriStrictMatching(oidcMongo.isRedirectUriStrictMatching());
        oidcSettings.setClientRegistrationSettings(convert(oidcMongo.getClientRegistrationSettings()));
        oidcSettings.setSecurityProfileSettings(convert(oidcMongo.getSecurityProfileSettings()));
        oidcSettings.setCibaSettings(convert(oidcMongo.getCibaSettings()));
        oidcSettings.setPostLogoutRedirectUris(oidcMongo.getPostLogoutRedirectUris());
        oidcSettings.setRequestUris(oidcMongo.getRequestUris());

        return oidcSettings;
    }

    private static UMASettings convert(UMASettingsMongo umaMongo) {
        if (umaMongo == null) {
            return null;
        }

        UMASettings umaSettings = new UMASettings();
        umaSettings.setEnabled(umaMongo.isEnabled());
        return umaSettings;
    }

    private static ClientRegistrationSettings convert(ClientRegistrationSettingsMongo dcrMongo) {
        if (dcrMongo == null) {
            return null;
        }

        ClientRegistrationSettings result = new ClientRegistrationSettings();
        result.setAllowHttpSchemeRedirectUri(dcrMongo.isAllowHttpSchemeRedirectUri());
        result.setAllowLocalhostRedirectUri(dcrMongo.isAllowLocalhostRedirectUri());
        result.setAllowWildCardRedirectUri(dcrMongo.isAllowWildCardRedirectUri());
        result.setDynamicClientRegistrationEnabled(dcrMongo.isDynamicClientRegistrationEnabled());
        result.setOpenDynamicClientRegistrationEnabled(dcrMongo.isOpenDynamicClientRegistrationEnabled());
        result.setDefaultScopes(dcrMongo.getDefaultScopes());
        result.setAllowedScopesEnabled(dcrMongo.isAllowedScopesEnabled());
        result.setAllowedScopes(dcrMongo.getAllowedScopes());
        result.setClientTemplateEnabled(dcrMongo.isClientTemplateEnabled());

        return result;
    }

    private static SecurityProfileSettings convert(SecurityProfileSettingsMongo profiles) {
        if (profiles == null) {
            return null;
        }

        SecurityProfileSettings result = new SecurityProfileSettings();
        result.setEnablePlainFapi(profiles.isEnablePlainFapi());
        result.setEnableFapiBrazil(profiles.isEnableFapiBrazil());

        return result;
    }

    private static CIBASettings convert(CIBASettingsMongo cibaSettings) {
        if (cibaSettings == null) {
            return null;
        }

        CIBASettings result = new CIBASettings();
        result.setEnabled(cibaSettings.isEnabled());
        result.setAuthReqExpiry(cibaSettings.getAuthReqExpiry());
        result.setTokenReqInterval(cibaSettings.getTokenReqInterval());
        result.setBindingMessageLength(cibaSettings.getBindingMessageLength());
        if (cibaSettings.getDeviceNotifiers() != null) {
            result.setDeviceNotifiers(cibaSettings.getDeviceNotifiers().stream().map(MongoDomainRepository::convert).collect(Collectors.toList()));
        }

        return result;
    }

    private static CIBASettingNotifier convert(CIBASettingNotifierMongo entity) {
        CIBASettingNotifier notifier = new CIBASettingNotifier();
        notifier.setId(entity.getId());
        return notifier;
    }

    private static CIBASettingNotifierMongo convert(CIBASettingNotifier entity) {
        CIBASettingNotifierMongo notifier = new CIBASettingNotifierMongo();
        notifier.setId(entity.getId());
        return notifier;
    }

    private static OIDCSettingsMongo convert(OIDCSettings oidc) {
        if (oidc == null) {
            return null;
        }

        OIDCSettingsMongo oidcSettings = new OIDCSettingsMongo();
        oidcSettings.setRedirectUriStrictMatching(oidc.isRedirectUriStrictMatching());
        oidcSettings.setClientRegistrationSettings(convert(oidc.getClientRegistrationSettings()));
        oidcSettings.setSecurityProfileSettings(convert(oidc.getSecurityProfileSettings()));
        oidcSettings.setCibaSettings(convert(oidc.getCibaSettings()));
        oidcSettings.setPostLogoutRedirectUris(oidc.getPostLogoutRedirectUris());
        oidcSettings.setRequestUris(oidc.getRequestUris());

        return oidcSettings;
    }

    private static UMASettingsMongo convert(UMASettings uma) {
        if (uma == null) {
            return null;
        }

        UMASettingsMongo umaMongo = new UMASettingsMongo();
        umaMongo.setEnabled(uma.isEnabled());
        return umaMongo;
    }

    private static ClientRegistrationSettingsMongo convert(ClientRegistrationSettings dcr) {
        if (dcr == null) {
            return null;
        }

        ClientRegistrationSettingsMongo result = new ClientRegistrationSettingsMongo();
        result.setAllowHttpSchemeRedirectUri(dcr.isAllowHttpSchemeRedirectUri());
        result.setAllowLocalhostRedirectUri(dcr.isAllowLocalhostRedirectUri());
        result.setAllowWildCardRedirectUri(dcr.isAllowWildCardRedirectUri());
        result.setDynamicClientRegistrationEnabled(dcr.isDynamicClientRegistrationEnabled());
        result.setOpenDynamicClientRegistrationEnabled(dcr.isOpenDynamicClientRegistrationEnabled());
        result.setDefaultScopes(dcr.getDefaultScopes());
        result.setAllowedScopesEnabled(dcr.isAllowedScopesEnabled());
        result.setAllowedScopes(dcr.getAllowedScopes());
        result.setClientTemplateEnabled(dcr.isClientTemplateEnabled());

        return result;
    }

    private static SecurityProfileSettingsMongo convert(SecurityProfileSettings profile) {
        if (profile == null) {
            return null;
        }

        SecurityProfileSettingsMongo result = new SecurityProfileSettingsMongo();
        result.setEnablePlainFapi(profile.isEnablePlainFapi());
        result.setEnableFapiBrazil(profile.isEnableFapiBrazil());

        return result;
    }

    private static CIBASettingsMongo convert(CIBASettings cibaSettings) {
        if (cibaSettings == null) {
            return null;
        }

        CIBASettingsMongo result = new CIBASettingsMongo();
        result.setEnabled(cibaSettings.isEnabled());
        result.setAuthReqExpiry(cibaSettings.getAuthReqExpiry());
        result.setTokenReqInterval(cibaSettings.getTokenReqInterval());
        result.setBindingMessageLength(cibaSettings.getBindingMessageLength());
        if (cibaSettings.getDeviceNotifiers() != null) {
            result.setDeviceNotifiers(cibaSettings.getDeviceNotifiers().stream().map(MongoDomainRepository::convert).collect(Collectors.toList()));
        }

        return result;
    }

    private static SCIMSettings convert(SCIMSettingsMongo scimMongo) {
        if (scimMongo == null) {
            return null;
        }

        SCIMSettings scimSettings = new SCIMSettings();
        scimSettings.setEnabled(scimMongo.isEnabled());
        scimSettings.setIdpSelectionEnabled(scimMongo.isIdpSelectionEnabled());
        scimSettings.setIdpSelectionRule(scimMongo.getIdpSelectionRule());
        return scimSettings;
    }

    private static SCIMSettingsMongo convert(SCIMSettings scim) {
        if (scim == null) {
            return null;
        }

        SCIMSettingsMongo scimMongo = new SCIMSettingsMongo();
        scimMongo.setEnabled(scim.isEnabled());
        scimMongo.setIdpSelectionEnabled(scim.isIdpSelectionEnabled());
        scimMongo.setIdpSelectionRule(scim.getIdpSelectionRule());
        return scimMongo;
    }

    private static LoginSettings convert(LoginSettingsMongo loginSettingsMongo) {
        return loginSettingsMongo != null ? loginSettingsMongo.convert() : null;
    }

    private static LoginSettingsMongo convert(LoginSettings loginSettings) {
        return LoginSettingsMongo.convert(loginSettings);
    }

    private static WebAuthnSettings convert(WebAuthnSettingsMongo webAuthnSettingsMongo) {
        if (webAuthnSettingsMongo == null) {
            return null;
        }

        WebAuthnSettings webAuthnSettings = new WebAuthnSettings();
        webAuthnSettings.setOrigin(webAuthnSettingsMongo.getOrigin());
        webAuthnSettings.setRelyingPartyId(webAuthnSettingsMongo.getRelyingPartyId());
        webAuthnSettings.setRelyingPartyName(webAuthnSettingsMongo.getRelyingPartyName());
        webAuthnSettings.setRequireResidentKey(webAuthnSettingsMongo.isRequireResidentKey());
        webAuthnSettings.setUserVerification(webAuthnSettingsMongo.getUserVerification() != null ?
                UserVerification.fromString(webAuthnSettingsMongo.getUserVerification()) : null);
        webAuthnSettings.setAuthenticatorAttachment(webAuthnSettingsMongo.getAuthenticatorAttachment() != null ?
                AuthenticatorAttachment.fromString(webAuthnSettingsMongo.getAuthenticatorAttachment()) : null);
        webAuthnSettings.setAttestationConveyancePreference(webAuthnSettingsMongo.getAttestationConveyancePreference() != null ?
                AttestationConveyancePreference.fromString(webAuthnSettingsMongo.getAttestationConveyancePreference()) : null);
        webAuthnSettings.setForceRegistration(webAuthnSettingsMongo.isForceRegistration());
        webAuthnSettings.setCertificates(webAuthnSettingsMongo.getCertificates());
        webAuthnSettings.setEnforceAuthenticatorIntegrity(webAuthnSettingsMongo.isEnforceAuthenticatorIntegrity());
        webAuthnSettings.setEnforceAuthenticatorIntegrityMaxAge(webAuthnSettingsMongo.getEnforceAuthenticatorIntegrityMaxAge());

        return webAuthnSettings;
    }

    private static WebAuthnSettingsMongo convert(WebAuthnSettings webAuthnSettings) {
        if (webAuthnSettings == null) {
            return null;
        }

        WebAuthnSettingsMongo webAuthnSettingsMongo = new WebAuthnSettingsMongo();
        webAuthnSettingsMongo.setOrigin(webAuthnSettings.getOrigin());
        webAuthnSettingsMongo.setRelyingPartyId(webAuthnSettings.getRelyingPartyId());
        webAuthnSettingsMongo.setRelyingPartyName(webAuthnSettings.getRelyingPartyName());
        webAuthnSettingsMongo.setRequireResidentKey(webAuthnSettings.isRequireResidentKey());
        webAuthnSettingsMongo.setUserVerification(webAuthnSettings.getUserVerification() != null ? webAuthnSettings.getUserVerification().getValue() : null);
        webAuthnSettingsMongo.setAuthenticatorAttachment(webAuthnSettings.getAuthenticatorAttachment() != null ? webAuthnSettings.getAuthenticatorAttachment().getValue() : null);
        webAuthnSettingsMongo.setAttestationConveyancePreference(webAuthnSettings.getAttestationConveyancePreference() != null ? webAuthnSettings.getAttestationConveyancePreference().getValue() : null);
        webAuthnSettingsMongo.setForceRegistration(webAuthnSettings.isForceRegistration());
        webAuthnSettingsMongo.setCertificates(webAuthnSettings.getCertificates() != null ? new Document(webAuthnSettings.getCertificates()) : null);
        webAuthnSettingsMongo.setEnforceAuthenticatorIntegrity(webAuthnSettings.isEnforceAuthenticatorIntegrity());
        webAuthnSettingsMongo.setEnforceAuthenticatorIntegrityMaxAge(webAuthnSettings.getEnforceAuthenticatorIntegrityMaxAge());
        return webAuthnSettingsMongo;
    }

    private static AccountSettings convert(AccountSettingsMongo accountSettingsMongo) {
        return accountSettingsMongo != null ? accountSettingsMongo.convert() : null;
    }

    private static AccountSettingsMongo convert(AccountSettings accountSettings) {
        return AccountSettingsMongo.convert(accountSettings);
    }

    private static PasswordSettings convert(PasswordSettingsMongo passwordSettingsMongo) {
        return passwordSettingsMongo != null ? passwordSettingsMongo.convert() : null;
    }

    private static PasswordSettingsMongo convert(PasswordSettings passwordSettings) {
        return PasswordSettingsMongo.convert(passwordSettings);
    }

    private static SelfServiceAccountManagementSettings convert(SelfServiceAccountManagementSettingsMongo selfAccountManagementSettingsMongo) {
        return selfAccountManagementSettingsMongo != null ? selfAccountManagementSettingsMongo.convert() : null;
    }

    private static SelfServiceAccountManagementSettingsMongo convert(SelfServiceAccountManagementSettings selfAccountManagementSettings) {
        return SelfServiceAccountManagementSettingsMongo.convert(selfAccountManagementSettings);
    }

    private static SAMLSettings convert(SAMLSettingsMongo samlMongo) {
        return samlMongo != null ? samlMongo.convert() : null;
    }

    private static SAMLSettingsMongo convert(SAMLSettings saml) {
        return SAMLSettingsMongo.convert(saml);
    }
}
