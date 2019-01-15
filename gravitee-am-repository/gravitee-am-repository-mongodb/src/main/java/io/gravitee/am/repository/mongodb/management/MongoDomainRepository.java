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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.event.Action;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.common.event.Type;
import io.gravitee.am.model.login.LoginForm;
import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.mongodb.common.IdGenerator;
import io.gravitee.am.repository.mongodb.management.internal.model.DomainMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.LoginFormMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.ClientRegistrationSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.OIDCSettingsMongo;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoDomainRepository extends AbstractManagementMongoRepository implements DomainRepository {

    private static final String FIELD_ID = "_id";
    private MongoCollection<DomainMongo> domainsCollection;

    @Autowired
    private IdGenerator idGenerator;

    @PostConstruct
    public void init() {
        domainsCollection = mongoOperations.getCollection("domains", DomainMongo.class);
    }

    @Override
    public Single<Set<Domain>> findAll() {
        return Observable.fromPublisher(domainsCollection.find()).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Maybe<Domain> findById(String id) {
        return Observable.fromPublisher(domainsCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<Set<Domain>> findByIdIn(Collection<String> ids) {
        return Observable.fromPublisher(domainsCollection.find(in(FIELD_ID, ids))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Domain> create(Domain item) {
        DomainMongo domain = convert(item);
        domain.setId(domain.getId() == null ? (String) idGenerator.generate() : domain.getId());
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

    private Domain convert(DomainMongo domainMongo) {
        if (domainMongo == null) {
            return null;
        }

        Domain domain = new Domain();
        domain.setId(domainMongo.getId());
        domain.setPath(domainMongo.getPath());
        domain.setCreatedAt(domainMongo.getCreatedAt());
        domain.setUpdatedAt(domainMongo.getUpdatedAt());
        domain.setName(domainMongo.getName());
        domain.setDescription(domainMongo.getDescription());
        domain.setEnabled(domainMongo.isEnabled());
        domain.setMaster(domainMongo.isMaster());
        domain.setLoginForm(convert(domainMongo.getLoginForm()));
        domain.setIdentities(domainMongo.getIdentities());
        domain.setOauth2Identities(domainMongo.getOauth2Identities());
        domain.setOidc(convert(domainMongo.getOidc()));

        // set last event
        Document document = domainMongo.getLastEvent();
        if (document != null) {
            domain.setLastEvent(convert(document));
        }

        return domain;
    }

    private DomainMongo convert(Domain domain) {
        if (domain == null) {
            return null;
        }

        DomainMongo domainMongo = new DomainMongo();
        domainMongo.setId(domain.getId());
        domainMongo.setPath(domain.getPath());
        domainMongo.setCreatedAt(domain.getCreatedAt());
        domainMongo.setUpdatedAt(domain.getUpdatedAt());
        domainMongo.setName(domain.getName());
        domainMongo.setDescription(domain.getDescription());
        domainMongo.setEnabled(domain.isEnabled());
        domainMongo.setMaster(domain.isMaster());
        domainMongo.setLoginForm(convert(domain.getLoginForm()));
        domainMongo.setIdentities(domain.getIdentities());
        domainMongo.setOauth2Identities(domain.getOauth2Identities());
        domainMongo.setOidc(convert(domain.getOidc()));

        // save last event
        Event event = domain.getLastEvent();
        if (event != null) {
            domainMongo.setLastEvent(convert(event));
        }

        return domainMongo;
    }

    private LoginForm convert(LoginFormMongo loginFormMongo) {
        if (loginFormMongo == null) {
            return null;
        }

        LoginForm loginForm = new LoginForm();
        loginForm.setEnabled(loginFormMongo.isEnabled());
        loginForm.setContent(loginFormMongo.getContent());
        loginForm.setAssets(loginFormMongo.getAssets());
        return loginForm;
    }

    private LoginFormMongo convert(LoginForm loginForm) {
        if (loginForm == null) {
            return null;
        }

        LoginFormMongo formMongo = new LoginFormMongo();
        formMongo.setEnabled(loginForm.isEnabled());
        formMongo.setContent(loginForm.getContent());
        formMongo.setAssets(loginForm.getAssets());
        return formMongo;
    }

    private Event convert(Document document) {
        Type type = Type.valueOf(document.get("type", String.class));
        Payload content = new Payload(document.get("content", Map.class));
        content.put("action", Action.valueOf((String) content.get("action")));
        Event event = new Event(type, content);

        return event;
    }

    private Document convert(Event event) {
        Document document = new Document();
        document.put("type", event.getType().toString());
        Payload payload = event.getPayload();
        payload.put("action", payload.getAction().toString());
        document.put("content", payload);

        return document;
    }

    private OIDCSettings convert(OIDCSettingsMongo oidcMongo) {
        if(oidcMongo==null) {
            return null;
        }

        OIDCSettings oidcSettings = new OIDCSettings();
        oidcSettings.setClientRegistrationSettings(convert(oidcMongo.getClientRegistrationSettings()));

        return oidcSettings;
    }

    private ClientRegistrationSettings convert(ClientRegistrationSettingsMongo dcrMongo) {
        if(dcrMongo==null) {
            return null;
        }

        ClientRegistrationSettings result = new ClientRegistrationSettings();
        result.setAllowHttpSchemeRedirectUri(dcrMongo.isAllowHttpSchemeRedirectUri());
        result.setAllowLocalhostRedirectUri(dcrMongo.isAllowLocalhostRedirectUri());
        result.setAllowWildCardRedirectUri(dcrMongo.isAllowWildCardRedirectUri());
        result.setDynamicClientRegistrationEnabled(dcrMongo.isDynamicClientRegistrationEnabled());
        result.setOpenDynamicClientRegistrationEnabled(dcrMongo.isOpenDynamicClientRegistrationEnabled());

        return result;
    }

    private OIDCSettingsMongo convert(OIDCSettings oidc) {
        if(oidc==null) {
            return null;
        }

        OIDCSettingsMongo oidcSettings = new OIDCSettingsMongo();
        oidcSettings.setClientRegistrationSettings(convert(oidc.getClientRegistrationSettings()));

        return oidcSettings;
    }

    private ClientRegistrationSettingsMongo convert(ClientRegistrationSettings dcr) {
        if(dcr==null) {
            return null;
        }

        ClientRegistrationSettingsMongo result = new ClientRegistrationSettingsMongo();
        result.setAllowHttpSchemeRedirectUri(dcr.isAllowHttpSchemeRedirectUri());
        result.setAllowLocalhostRedirectUri(dcr.isAllowLocalhostRedirectUri());
        result.setAllowWildCardRedirectUri(dcr.isAllowWildCardRedirectUri());
        result.setDynamicClientRegistrationEnabled(dcr.isDynamicClientRegistrationEnabled());
        result.setOpenDynamicClientRegistrationEnabled(dcr.isOpenDynamicClientRegistrationEnabled());

        return result;
    }
}
