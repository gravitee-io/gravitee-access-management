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
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Component
public class MongoPermissionTicketRepository extends AbstractManagementMongoRepository implements PermissionTicketRepository {

    private static final String FIELD_RESET_TIME = "expireAt";
    private static final String COLLECTION_NAME = "uma_permission_ticket";
    private MongoCollection<PermissionTicketMongo> permissionTicketCollection;

    @PostConstruct
    public void init() {
        permissionTicketCollection = mongoOperations.getCollection(COLLECTION_NAME, PermissionTicketMongo.class);
        super.init(permissionTicketCollection);

        // expire after index
        super.createIndex(permissionTicketCollection, new Document(FIELD_RESET_TIME, 1), new IndexOptions().expireAfter(0l, TimeUnit.SECONDS));
    }

    @Override
    public Maybe<PermissionTicket> findById(String id) {
        return Observable.fromPublisher(permissionTicketCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<PermissionTicket> create(PermissionTicket ticket) {
        PermissionTicketMongo permissionTicket = convert(ticket);
        permissionTicket.setId(permissionTicket.getId() == null ? RandomString.generate() : permissionTicket.getId());
        return Single.fromPublisher(permissionTicketCollection.insertOne(permissionTicket)).flatMap(success -> findById(permissionTicket.getId()).toSingle());
    }

    @Override
    public Single<PermissionTicket> update(PermissionTicket ticket) {
        PermissionTicketMongo permissionTicket = convert(ticket);
        return Single.fromPublisher(permissionTicketCollection.replaceOne(eq(FIELD_ID, permissionTicket.getId()), permissionTicket)).flatMap(success -> findById(permissionTicket.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(permissionTicketCollection.deleteOne(eq(FIELD_ID, id)));
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
