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
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.model.uma.PermissionTicket;
import io.gravitee.am.repository.management.api.PermissionTicketRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.uma.PermissionRequestMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.uma.PermissionTicketMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.or;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Component
public class MongoPermissionTicketRepository extends AbstractManagementMongoRepository implements PermissionTicketRepository {

    private static final String FIELD_EXPIRE_AT = "expireAt";
    private static final String COLLECTION_NAME = "uma_permission_ticket";
    private MongoCollection<PermissionTicketMongo> permissionTicketCollection;

    @PostConstruct
    public void init() {
        permissionTicketCollection = mongoOperations.getCollection(COLLECTION_NAME, PermissionTicketMongo.class);
        super.init(permissionTicketCollection);

        // expire after index
        super.createIndex(permissionTicketCollection, Map.of(new Document(FIELD_EXPIRE_AT, 1), new IndexOptions().expireAfter(0l, TimeUnit.SECONDS).name("e1")));
    }

    @Override
    public Maybe<PermissionTicket> findById(String id) {
        return Observable.fromPublisher(permissionTicketCollection.find(and(eq(FIELD_ID, id), or(gt(FIELD_EXPIRE_AT, new Date()), eq(FIELD_EXPIRE_AT, null)))).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<PermissionTicket> create(PermissionTicket item) {
        PermissionTicketMongo permissionTicket = convert(item);
        permissionTicket.setId(permissionTicket.getId() == null ? RandomString.generate() : permissionTicket.getId());
        return Single.fromPublisher(permissionTicketCollection.insertOne(permissionTicket)).flatMap(success -> { item.setId(permissionTicket.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<PermissionTicket> update(PermissionTicket item) {
        PermissionTicketMongo permissionTicket = convert(item);
        return Single.fromPublisher(permissionTicketCollection.replaceOne(eq(FIELD_ID, permissionTicket.getId()), permissionTicket)).flatMap(success -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(permissionTicketCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private PermissionTicketMongo convert(PermissionTicket permissionTicket) {
        return new PermissionTicketMongo()
                .setId(permissionTicket.getId())
                .setPermissionRequest(this.toMongo(permissionTicket.getPermissionRequest()))
                .setDomain(permissionTicket.getDomain())
                .setClientId(permissionTicket.getClientId())
                .setUserId(permissionTicket.getUserId())
                .setCreatedAt(permissionTicket.getCreatedAt())
                .setExpireAt(permissionTicket.getExpireAt());
    }

    private PermissionTicket convert(PermissionTicketMongo ptMongo) {
        return new PermissionTicket()
                .setId(ptMongo.getId())
                .setPermissionRequest(this.fromMongo(ptMongo.getPermissionRequest()))
                .setDomain(ptMongo.getDomain())
                .setClientId(ptMongo.getClientId())
                .setUserId(ptMongo.getUserId())
                .setCreatedAt(ptMongo.getCreatedAt())
                .setExpireAt(ptMongo.getExpireAt());
    }

    private List<PermissionRequest> fromMongo(List<PermissionRequestMongo> fromMongo) {
        if(fromMongo==null) {
            return null;
        }
        return fromMongo.stream().map(this::convert).collect(Collectors.toList());
    }

    private List<PermissionRequestMongo> toMongo(List<PermissionRequest> toMongo) {
        if(toMongo == null) {
            return null;
        }
        return toMongo.stream().map(this::convert).collect(Collectors.toList());
    }

    private PermissionRequest convert(PermissionRequestMongo fromMongo) {
        if(fromMongo==null) {
            return null;
        }
        return new PermissionRequest()
                .setResourceId(fromMongo.getResourceId())
                .setResourceScopes(fromMongo.getResourceScopes());
    }

    private PermissionRequestMongo convert(PermissionRequest toMongo) {
        if(toMongo == null) {
            return null;
        }
        return new PermissionRequestMongo()
                .setResourceId(toMongo.getResourceId())
                .setResourceScopes(toMongo.getResourceScopes());
    }
}
