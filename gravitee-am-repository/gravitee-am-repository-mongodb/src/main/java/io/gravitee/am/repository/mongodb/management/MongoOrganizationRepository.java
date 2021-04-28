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
import io.reactivex.*;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

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
        return Flowable.fromPublisher(collection.find(in(HRID_KEY, hrids))).map(this::convert);
    }

    @Override
    public Maybe<Organization> findById(String id) {

        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Single<Organization> create(Organization organization) {

        organization.setId(organization.getId() == null ? RandomString.generate() : organization.getId());

        return Single.fromPublisher(collection.insertOne(convert(organization)))
                .flatMap(success -> findById(organization.getId()).toSingle());
    }

    @Override
    public Single<Organization> update(Organization organization) {

        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, organization.getId()), convert(organization)))
                .flatMap(updateResult -> findById(organization.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)));
    }

    @Override
    public Single<Long> count() {

        return Single.fromPublisher(collection.countDocuments());
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
