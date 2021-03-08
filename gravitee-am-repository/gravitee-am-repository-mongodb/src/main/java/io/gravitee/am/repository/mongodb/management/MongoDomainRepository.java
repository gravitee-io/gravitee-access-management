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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.webauthn.AttestationConveyancePreference;
import io.gravitee.am.common.webauthn.AuthenticatorAttachment;
import io.gravitee.am.common.webauthn.UserVerification;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.model.uma.UMASettings;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AccountSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.DomainMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.LoginSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.SCIMSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.WebAuthnSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.ClientRegistrationSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.OIDCSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.uma.UMASettingsMongo;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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

    @PostConstruct
    public void init() {
        domainsCollection = mongoOperations.getCollection("domains", DomainMongo.class);
        super.init(domainsCollection);
    }

    @Override
    public Single<Set<Domain>> findAll() {
        return Observable.fromPublisher(domainsCollection.find()).map(MongoDomainRepository::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Maybe<Domain> findById(String id) {
        return Observable.fromPublisher(domainsCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(MongoDomainRepository::convert);
    }

    @Override
    public Single<Set<Domain>> findByIdIn(Collection<String> ids) {
        return Observable.fromPublisher(domainsCollection.find(in(FIELD_ID, ids))).map(MongoDomainRepository::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Flowable<Domain> findAllByEnvironment(String environmentId) {

        return Flowable.fromPublisher(domainsCollection.find(and(eq(FIELD_REFERENCE_TYPE, ReferenceType.ENVIRONMENT.name()), eq(FIELD_REFERENCE_ID, environmentId)))).map(MongoDomainRepository::convert);
    }

    @Override
    public Single<Domain> create(Domain item) {
        DomainMongo domain = convert(item);
        domain.setId(domain.getId() == null ? RandomString.generate() : domain.getId());
        return Single.fromPublisher(domainsCollection.insertOne(domain)).flatMap(success -> findById(domain.getId()).toSingle());
    }

    @Override
    public Single<Domain> update(Domain item) {
        DomainMongo domain = convert(item);
        return Single.fromPublisher(domainsCollection.replaceOne(eq(FIELD_ID, domain.getId()), domain)).flatMap(updateResult -> findById(domain.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(domainsCollection.deleteOne(eq(FIELD_ID, id)));
    }

    private static Domain convert(DomainMongo domainMongo) {
        if (domainMongo == null) {
            return null;
        }

        Domain domain = new Domain();
        domain.setId(domainMongo.getId());
        domain.setPath(domainMongo.getPath());
        domain.setVhostMode(domainMongo.isVhostMode());
        domain.setVhosts(domainMongo.getVhosts());
        domain.setCreatedAt(domainMongo.getCreatedAt());
        domain.setUpdatedAt(domainMongo.getUpdatedAt());
        domain.setName(domainMongo.getName());
        domain.setDescription(domainMongo.getDescription());
        domain.setEnabled(domainMongo.isEnabled());
        domain.setOidc(convert(domainMongo.getOidc()));
        domain.setUma(convert(domainMongo.getUma()));
        domain.setScim(convert(domainMongo.getScim()));
        domain.setLoginSettings(convert(domainMongo.getLoginSettings()));
        domain.setWebAuthnSettings(convert(domainMongo.getWebAuthnSettings()));
        domain.setAccountSettings(convert(domainMongo.getAccountSettings()));
        domain.setPasswordSettings(ConversionUtils.convert(domainMongo.getPasswordSettings()));
        domain.setTags(domainMongo.getTags());
        domain.setReferenceType(domainMongo.getReferenceType());
        domain.setReferenceId(domainMongo.getReferenceId());
        domain.setIdentities(domainMongo.getIdentities());
        return domain;
    }

    private static DomainMongo convert(Domain domain) {
        if (domain == null) {
            return null;
        }

        DomainMongo domainMongo = new DomainMongo();
        domainMongo.setId(domain.getId());
        domainMongo.setPath(domain.getPath());
        domainMongo.setVhostMode(domain.isVhostMode());
        domainMongo.setVhosts(domain.getVhosts());
        domainMongo.setCreatedAt(domain.getCreatedAt());
        domainMongo.setUpdatedAt(domain.getUpdatedAt());
        domainMongo.setName(domain.getName());
        domainMongo.setDescription(domain.getDescription());
        domainMongo.setEnabled(domain.isEnabled());
        domainMongo.setOidc(convert(domain.getOidc()));
        domainMongo.setUma(convert(domain.getUma()));
        domainMongo.setScim(convert(domain.getScim()));
        domainMongo.setLoginSettings(convert(domain.getLoginSettings()));
        domainMongo.setWebAuthnSettings(convert(domain.getWebAuthnSettings()));
        domainMongo.setAccountSettings(convert(domain.getAccountSettings()));
        domainMongo.setPasswordSettings(ConversionUtils.convert(domain.getPasswordSettings()));
        domainMongo.setTags(domain.getTags());
        domainMongo.setReferenceType(domain.getReferenceType());
        domainMongo.setReferenceId(domain.getReferenceId());
        domainMongo.setIdentities(domain.getIdentities());
        return domainMongo;
    }

    private static OIDCSettings convert(OIDCSettingsMongo oidcMongo) {
        if (oidcMongo == null) {
            return null;
        }

        OIDCSettings oidcSettings = new OIDCSettings();
        oidcSettings.setRedirectUriStrictMatching(oidcMongo.isRedirectUriStrictMatching());
        oidcSettings.setClientRegistrationSettings(convert(oidcMongo.getClientRegistrationSettings()));

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

    private static OIDCSettingsMongo convert(OIDCSettings oidc) {
        if (oidc == null) {
            return null;
        }

        OIDCSettingsMongo oidcSettings = new OIDCSettingsMongo();
        oidcSettings.setRedirectUriStrictMatching(oidc.isRedirectUriStrictMatching());
        oidcSettings.setClientRegistrationSettings(convert(oidc.getClientRegistrationSettings()));

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

    private static SCIMSettings convert(SCIMSettingsMongo scimMongo) {
        if (scimMongo == null) {
            return null;
        }

        SCIMSettings scimSettings = new SCIMSettings();
        scimSettings.setEnabled(scimMongo.isEnabled());
        return scimSettings;
    }

    private static SCIMSettingsMongo convert(SCIMSettings scim) {
        if (scim == null) {
            return null;
        }

        SCIMSettingsMongo scimMongo = new SCIMSettingsMongo();
        scimMongo.setEnabled(scim.isEnabled());
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
        return webAuthnSettingsMongo;
    }

    private static AccountSettings convert(AccountSettingsMongo accountSettingsMongo) {
        return accountSettingsMongo != null ? accountSettingsMongo.convert() : null;
    }

    private static AccountSettingsMongo convert(AccountSettings accountSettings) {
        return AccountSettingsMongo.convert(accountSettings);
    }

}
