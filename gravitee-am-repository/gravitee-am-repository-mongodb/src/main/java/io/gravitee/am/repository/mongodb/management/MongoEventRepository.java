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
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.token.RevokeToken;
import io.gravitee.am.model.token.RevokeType;
import io.gravitee.am.repository.management.api.EventRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.EventMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.or;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_UPDATED_AT;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoEventRepository extends AbstractManagementMongoRepository implements EventRepository {

    private static final Logger log = LoggerFactory.getLogger(MongoEventRepository.class);
    public static final String ACTION = "action";
    public static final String DATA_PLANE_ID = "dataPlaneId";
    private MongoCollection<EventMongo> eventsCollection;

    /**
     * Event retention period in days. Events older than this period will be purged.
     * Default: 90 days (3 months)
     * Configuration: services.purge.events.retention.days
     */
    @Value("${services.purge.events.retention.days:90}")
    private int eventsRetentionDays;

    @PostConstruct
    public void init() {
        eventsCollection = mongoOperations.getCollection("events", EventMongo.class);
        super.init(eventsCollection);
        super.createIndex(eventsCollection, Map.of(new Document(FIELD_UPDATED_AT, 1), new IndexOptions().name("u1")));
        super.createIndex(eventsCollection, Map.of(new Document(FIELD_UPDATED_AT, 1).append(DATA_PLANE_ID, 1), new IndexOptions().name("u1dp1")));
        super.createIndex(eventsCollection, Map.of(new Document(FIELD_CREATED_AT, 1), new IndexOptions().name("e1").expireAfter((long) eventsRetentionDays, TimeUnit.DAYS)));
    }

    @Override
    public Flowable<Event> findByTimeFrame(long from, long to) {
        List<Bson> filters = new ArrayList<>();
        filters.add(gte(FIELD_UPDATED_AT, new Date(from)));
        if (to > from) {
            filters.add(lte(FIELD_UPDATED_AT, new Date(to)));
        }
        return Flowable.fromPublisher(withMaxTime(eventsCollection.find(and(filters)))).map(this::convert);
    }

    @Override
    public Flowable<Event> findByTimeFrameAndDataPlaneId(long from, long to, String dataPlaneId) {
        List<Bson> filters = new ArrayList<>();
        filters.add(gte(FIELD_UPDATED_AT, new Date(from)));
        if (to > from) {
            filters.add(lte(FIELD_UPDATED_AT, new Date(to)));
        }
        filters.add(or(eq(DATA_PLANE_ID, dataPlaneId), eq(DATA_PLANE_ID, null)));
        return Flowable.fromPublisher(withMaxTime(eventsCollection.find(and(filters)))).map(this::convert);
    }

    @Override
    public Maybe<Event> findById(String id) {
        return Observable.fromPublisher(eventsCollection.find(eq(FIELD_ID, id)).first()).map(this::convert).firstElement();
    }

    @Override
    public Single<Event> create(Event item) {
        EventMongo event = convert(item);
        event.setId(event.getId() == null ? RandomString.generate() : event.getId());
        return Single.fromPublisher(eventsCollection.insertOne(event)).flatMap(success -> { item.setId(event.getId()); return Single.just(item); });
    }

    @Override
    public Single<Event> update(Event item) {
        EventMongo event = convert(item);
        return Single.fromPublisher(eventsCollection.replaceOne(eq(FIELD_ID, event.getId()), event)).flatMap(updateResult -> Single.just(item));
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(eventsCollection.deleteOne(eq(FIELD_ID, id)));
    }

    private EventMongo convert(Event event) {
        if (event == null) {
            return null;
        }

        EventMongo eventMongo = new EventMongo();
        eventMongo.setId(event.getId());
        eventMongo.setType(event.getType().toString());
        eventMongo.setPayload(convert(event.getPayload()));
        eventMongo.setCreatedAt(event.getCreatedAt());
        eventMongo.setUpdatedAt(event.getUpdatedAt());
        eventMongo.setDataPlaneId(event.getDataPlaneId());
        eventMongo.setEnvironmentId(event.getEnvironmentId());
        return eventMongo;
    }

    private Event convert(EventMongo eventMongo) {
        if (eventMongo == null) {
            return null;
        }

        Event event = new Event();
        event.setId(eventMongo.getId());
        event.setPayload(convert(eventMongo.getPayload()));
        event.setCreatedAt(eventMongo.getCreatedAt());
        event.setUpdatedAt(eventMongo.getUpdatedAt());
        try {
            event.setType(Type.valueOf(eventMongo.getType()));
        } catch (IllegalArgumentException e) {
            log.info("Invalid event type '{}', the event will be ignored by synchronization process.", eventMongo.getType());
            event.setType(Type.UNKNOWN);
        }
        event.setDataPlaneId(eventMongo.getDataPlaneId());
        event.setEnvironmentId(eventMongo.getEnvironmentId());

        return event;
    }

    private Payload convert(Document document) {
        if (document == null) {
            return null;
        }

        Map content = document.get("content", Map.class);
        final var optRevokeToken = ofNullable(content.get(Payload.REVOKE_TOKEN_DEFINITION));
        Payload payload = new Payload(content);
        payload.put(ACTION, Action.valueOf((String) payload.get(ACTION)));
        optRevokeToken.filter(obj -> obj instanceof Map)
                .ifPresent(obj -> payload.put(Payload.REVOKE_TOKEN_DEFINITION, toRevokeToken((Map) obj)));
        return payload;
    }

    private Document convert(Payload payload) {
        if (payload == null) {
            return null;
        }

        Document document = new Document();
        payload.put(ACTION, payload.getAction().toString());
        document.put("content", payload);

        return document;
    }

    private Document convert(RevokeToken revokeToken) {
        if (revokeToken == null) {
            return null;
        }

        Document document = new Document();
        document.put("revokeType", revokeToken.getRevokeType().name());
        document.put("domainId", revokeToken.getDomainId());
        document.put("clientId", revokeToken.getClientId());
        document.put("userId", convert(revokeToken.getUserId()));

        return document;
    }

    private RevokeToken toRevokeToken(Map revokeToken) {
        if (revokeToken == null) {
            return null;
        }

        RevokeToken document = new RevokeToken();
        document.setRevokeType(RevokeType.valueOf((String) revokeToken.get("revokeType")));
        document.setDomainId((String) revokeToken.get("domainId"));
        document.setClientId((String) revokeToken.get("clientId"));
        document.setUserId(toUserId((Map) revokeToken.get("userId")));

        return document;
    }

    private Document convert(UserId userId) {
        if (userId == null) {
            return null;
        }

        Document document = new Document();
        document.put("id", userId.id());
        document.put("externalId", userId.externalId());
        document.put("source", userId.source());

        return document;
    }


    private UserId toUserId(Map userId) {
        if (userId == null) {
            return null;
        }
        return new UserId((String) userId.get("id"), (String) userId.get("externalId"), (String) userId.get("source"));
    }
}
