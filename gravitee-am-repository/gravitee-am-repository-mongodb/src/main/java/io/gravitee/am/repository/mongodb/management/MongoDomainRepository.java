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
import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.model.login.LoginForm;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.mongodb.common.IdGenerator;
import io.gravitee.am.repository.mongodb.management.internal.model.DomainMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.LoginFormMongo;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashSet;
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
        return _findById(id).toMaybe();
    }

    @Override
    public Single<Set<Domain>> findByIdIn(Collection<String> ids) {
        return Observable.fromPublisher(domainsCollection.find(in(FIELD_ID, ids))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Domain> create(Domain item) {
        DomainMongo domain = convert(item);
        domain.setId(domain.getId() == null ? (String) idGenerator.generate() : domain.getId());
        return Single.fromPublisher(domainsCollection.insertOne(domain)).flatMap(success -> _findById(domain.getId()));
    }

    @Override
    public Single<Domain> update(Domain item) {
        DomainMongo domain = convert(item);
        return Single.fromPublisher(domainsCollection.replaceOne(eq(FIELD_ID, domain.getId()), domain)).flatMap(updateResult -> _findById(domain.getId()));
    }

    @Override
    public Single<Irrelevant> delete(String id) {
        return Single.fromPublisher(domainsCollection.deleteOne(eq(FIELD_ID, id))).map(deleteResult -> Irrelevant.DOMAIN);
    }

    private Single<Domain> _findById(String id) {
        return Single.fromPublisher(domainsCollection.find(eq(FIELD_ID, id)).first()).map(this::convert);
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
}
