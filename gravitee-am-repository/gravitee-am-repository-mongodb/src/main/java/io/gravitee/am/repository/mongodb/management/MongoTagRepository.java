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

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Tag;
import io.gravitee.am.repository.management.api.TagRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.TagMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ORGANIZATION_ID;
/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoTagRepository extends AbstractManagementMongoRepository implements TagRepository {

    private MongoCollection<TagMongo> tagsCollection;

    @PostConstruct
    public void init() {
        tagsCollection = mongoOperations.getCollection("tags", TagMongo.class);
        super.init(tagsCollection);
        super.createIndex(tagsCollection, Map.of(new Document(FIELD_ORGANIZATION_ID, 1), new IndexOptions().name("o1")));
    }


    @Override
    public Maybe<Tag> findById(String id, String organizationId) {
        return Observable.fromPublisher(tagsCollection.find(and(eq(FIELD_ID, id), eq(FIELD_ORGANIZATION_ID, organizationId))).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Tag> findById(String id) {
        return Observable.fromPublisher(tagsCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Tag> findAll(String organizationId) {
        return Flowable.fromPublisher(withMaxTime(tagsCollection.find(eq(FIELD_ORGANIZATION_ID, organizationId)))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Tag> create(Tag item) {
        TagMongo tag = convert(item);
        tag.setId(tag.getId() == null ? RandomString.generate() : tag.getId());
        return Single.fromPublisher(tagsCollection.insertOne(tag)).flatMap(success -> { item.setId(tag.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Tag> update(Tag item) {
        TagMongo tag = convert(item);
        return Single.fromPublisher(tagsCollection.replaceOne(eq(FIELD_ID, tag.getId()), tag)).flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(tagsCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private Tag convert(TagMongo tagMongo) {
        if (tagMongo == null) {
            return null;
        }

        Tag tag = new Tag();
        tag.setId(tagMongo.getId());
        tag.setName(tagMongo.getName());
        tag.setDescription(tagMongo.getDescription());
        tag.setOrganizationId(tagMongo.getOrganizationId());
        tag.setCreatedAt(tagMongo.getCreatedAt());
        tag.setUpdatedAt(tagMongo.getUpdatedAt());

        return tag;
    }

    private TagMongo convert(Tag tag) {
        if (tag == null) {
            return null;
        }

        TagMongo tagMongo = new TagMongo();
        tagMongo.setId(tag.getId());
        tagMongo.setName(tag.getName());
        tagMongo.setDescription(tag.getDescription());
        tagMongo.setOrganizationId(tag.getOrganizationId());
        tagMongo.setCreatedAt(tag.getCreatedAt());
        tagMongo.setUpdatedAt(tag.getUpdatedAt());

        return tagMongo;
    }
}
