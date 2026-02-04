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
import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.OrganizationMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoOrganizationRepository extends AbstractManagementMongoRepository implements OrganizationRepository {

    private MongoCollection<OrganizationMongo> collection;
    private static final String HRID_KEY = "hrids";

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("organizations", OrganizationMongo.class);
        super.init(collection);
    }

    @Override
    public Flowable<Organization> findByHrids(List<String> hrids) {
        return Flowable.fromPublisher(withMaxTime(collection.find(in(HRID_KEY, hrids)))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Organization> findAll() {
        return Flowable.fromPublisher(withMaxTime(collection.find()))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Organization> findById(String id) {

        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Organization> create(Organization item) {
        final OrganizationMongo organization = convert(item);
        organization.setId(item.getId() == null ? RandomString.generate() : item.getId());

        return Single.fromPublisher(collection.insertOne(organization))
                .flatMap(success -> { item.setId(organization.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Organization> update(Organization item) {
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, item.getId()), convert(item)))
                .flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Long> count() {

        return Single.fromPublisher(collection.countDocuments())
                .observeOn(Schedulers.computation());
    }

    private Organization convert(OrganizationMongo organizationMongo) {

        Organization organization = new Organization();
        organization.setId(organizationMongo.getId());
        organization.setHrids(organizationMongo.getHrids());
        organization.setDescription(organizationMongo.getDescription());
        organization.setName(organizationMongo.getName());
        organization.setDomainRestrictions(organizationMongo.getDomainRestrictions());
        organization.setIdentities(organizationMongo.getIdentities());
        organization.setCreatedAt(organizationMongo.getCreatedAt());
        organization.setUpdatedAt(organizationMongo.getUpdatedAt());

        return organization;
    }

    private OrganizationMongo convert(Organization organization) {

        OrganizationMongo organizationMongo = new OrganizationMongo();
        organizationMongo.setId(organization.getId());
        organizationMongo.setHrids(organization.getHrids());
        organizationMongo.setDescription(organization.getDescription());
        organizationMongo.setName(organization.getName());
        organizationMongo.setDomainRestrictions(organization.getDomainRestrictions());
        organizationMongo.setIdentities(organization.getIdentities());
        organizationMongo.setCreatedAt(organization.getCreatedAt());
        organizationMongo.setUpdatedAt(organization.getUpdatedAt());

        return organizationMongo;
    }
}
