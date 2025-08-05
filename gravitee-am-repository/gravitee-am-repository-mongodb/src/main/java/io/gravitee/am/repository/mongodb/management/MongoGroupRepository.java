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
import io.gravitee.am.model.Group;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.GroupRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.repository.mongodb.management.internal.model.GroupMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static io.gravitee.am.model.common.Page.pageFromOffset;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoGroupRepository extends AbstractManagementMongoRepository implements GroupRepository {
    // TODO [DP] class to remove
    private final static Logger LOGGER = LoggerFactory.getLogger(MongoGroupRepository.class);
    private static final String FIELD_MEMBERS = "members";
    private static final String FIELD_NAME = "name";
    private static final String DISPLAY_NAME = "displayName";
    private MongoCollection<GroupMongo> groupsCollection;

    private final Set<String> UNUSED_INDEXES = Set.of("rt1ri1");


    @PostConstruct
    public void init() {
        groupsCollection = mongoOperations.getCollection("groups", GroupMongo.class);
        super.init(groupsCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_NAME, 1), new IndexOptions().name("rt1ri1n1"));

        super.createIndex(groupsCollection, indexes);
        if (ensureIndexOnStart) {
            dropIndexes(groupsCollection, UNUSED_INDEXES::contains).subscribe();
        }
    }

    @Override
    public Flowable<Group> findByMember(String memberId) {
        return Flowable.fromPublisher(withMaxTime(groupsCollection.find(eq(FIELD_MEMBERS, memberId)))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Group> findAll(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(withMaxTime(groupsCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId))))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<Group>> findAllScim(ReferenceType referenceType, String referenceId, int offset, int size) {
        Single<Long> countOperation = Observable.fromPublisher(groupsCollection.countDocuments(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId)), countOptions())).first(0l);
        Single<List<Group>> groupsOperation = Observable.fromPublisher(withMaxTime(groupsCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId)))).sort(new BasicDBObject(FIELD_NAME, 1)).skip(offset).limit(size)).map(this::convert).collect(LinkedList::new, List::add);
        return Single.zip(countOperation, groupsOperation, (count, groups) -> new Page<>(groups, pageFromOffset(offset, size), count))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<Group>> searchScim(ReferenceType referenceType, String referenceId, FilterCriteria criteria, int offset, int size) {
        try {
            final FilterCriteria mappedCriteria = mapCriteria(criteria);
            BasicDBObject searchQuery = BasicDBObject.parse(filterCriteriaParser.parse(mappedCriteria));

            Bson mongoQuery = and(
                    eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                    eq(FIELD_REFERENCE_ID, referenceId),
                    searchQuery);
            Single<Long> countOperation = Observable.fromPublisher(groupsCollection.countDocuments(mongoQuery, countOptions())).first(0l);
            Single<List<Group>> groupsOperation = Observable.fromPublisher(withMaxTime(groupsCollection.find(mongoQuery)).sort(new BasicDBObject(FIELD_NAME, 1)).skip(offset).limit(size)).map(this::convert).collect(LinkedList::new, List::add);
            return Single.zip(countOperation, groupsOperation, (count, groups) -> new Page<>(groups, pageFromOffset(offset, size), count))
                    .observeOn(Schedulers.computation());
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException) {
                return Single.error(ex);
            }
            LOGGER.error("An error has occurred while searching groups with criteria {}", criteria, ex);
            return Single.error(new TechnicalException("An error has occurred while searching groups with filter criteria", ex));
        }
    }

    @Override
    public Flowable<Group> findByIdIn(List<String> ids) {
        return Flowable.fromPublisher(withMaxTime(groupsCollection.find(in(FIELD_ID, ids)))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Group> findByName(ReferenceType referenceType, String referenceId, String groupName) {
        return Observable.fromPublisher(
                groupsCollection
                        .find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_NAME, groupName)))
                        .limit(1)
                        .first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Group> findById(ReferenceType referenceType, String referenceId, String group) {
        return Observable.fromPublisher(groupsCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_ID, group))).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Group> findById(String group) {
        return Observable.fromPublisher(groupsCollection.find(eq(FIELD_ID, group)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Group> create(Group item) {
        GroupMongo group = convert(item);
        group.setId(group.getId() == null ? RandomString.generate() : group.getId());
        return Single.fromPublisher(groupsCollection.insertOne(group)).flatMap(success -> { item.setId(group.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Group> update(Group item) {
        GroupMongo group = convert(item);
        return Single.fromPublisher(groupsCollection.replaceOne(eq(FIELD_ID, group.getId()), group)).flatMap(success -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(groupsCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private Group convert(GroupMongo groupMongo) {
        if (groupMongo == null) {
            return null;
        }
        Group group = new Group();
        group.setId(groupMongo.getId());
        group.setReferenceType(ReferenceType.valueOf(groupMongo.getReferenceType()));
        group.setReferenceId(groupMongo.getReferenceId());
        group.setName(groupMongo.getName());
        group.setDescription(groupMongo.getDescription());
        group.setMembers(groupMongo.getMembers());
        group.setRoles(groupMongo.getRoles());
        group.setCreatedAt(groupMongo.getCreatedAt());
        group.setUpdatedAt(groupMongo.getUpdatedAt());
        return group;
    }

    private GroupMongo convert(Group group) {
        if (group == null) {
            return null;
        }
        GroupMongo groupMongo = new GroupMongo();
        groupMongo.setId(group.getId());
        groupMongo.setReferenceType(group.getReferenceType().name());
        groupMongo.setReferenceId(group.getReferenceId());
        groupMongo.setName(group.getName());
        groupMongo.setDescription(group.getDescription());
        groupMongo.setMembers(group.getMembers());
        groupMongo.setRoles(group.getRoles());
        groupMongo.setCreatedAt(group.getCreatedAt());
        groupMongo.setUpdatedAt(group.getUpdatedAt());
        return groupMongo;
    }

    private FilterCriteria mapCriteria(FilterCriteria criteria) {
        if(DISPLAY_NAME.equals(criteria.getFilterName())) {
            criteria.setFilterName(FIELD_NAME);
        }

        return criteria;
    }
}
