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
package io.gravitee.am.repository.mongodb.gateway;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.command.CommandStaging;
import io.gravitee.am.repository.gateway.api.CommandStagingRepository;
import io.gravitee.am.repository.mongodb.gateway.internal.model.CommandStagingMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author GraviteeSource Team
 */
@Component
@Slf4j
public class MongoCommandStagingRepository extends AbstractGatewayMongoRepository implements CommandStagingRepository {

    private static final String COLLECTION_NAME = "dp_command_staging";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_REFERENCE_TYPE = "referenceType";
    private static final String FIELD_REFERENCE_ID = "referenceId";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    private MongoCollection<CommandStagingMongo> commandStagingCollection;

    @PostConstruct
    public void init() {
        commandStagingCollection = mongoOperations.getCollection(COLLECTION_NAME, CommandStagingMongo.class);
        super.init(commandStagingCollection);

        var indexes = Map.of(
                new Document(FIELD_REFERENCE_TYPE, 1)
                        .append(FIELD_REFERENCE_ID, 1)
                        .append(FIELD_UPDATED_AT, 1),
                new IndexOptions().name("updated_at_idx")
        );

        super.createIndex(commandStagingCollection, indexes, getEnsureIndexOnStart());
    }

    @Override
    public Maybe<CommandStaging> createIfAbsent(CommandStaging commandStaging) {
        CommandStagingMongo commandStagingMongo = convert(commandStaging);

        Date now = new Date();
        commandStagingMongo.setCreatedAt(now);
        commandStagingMongo.setUpdatedAt(now);

        return Single.fromPublisher(commandStagingCollection.insertOne(commandStagingMongo))
                .map(success -> convert(commandStagingMongo))
                .toMaybe()
                .onErrorResumeNext(error -> {
                    if (isDuplicateKey(error)) {
                        log.debug("Command staging {} already exists, another node staged it first", commandStaging.getId());
                        return Maybe.empty();
                    }
                    return Maybe.error(error);
                })
                .observeOn(Schedulers.computation());
    }

    private static boolean isDuplicateKey(Throwable error) {
        return error instanceof MongoWriteException writeException
                && writeException.getError().getCategory() == ErrorCategory.DUPLICATE_KEY;
    }

    @Override
    public Flowable<CommandStaging> findOldestByUpdateDate(Reference reference, int limit) {
        return Observable.fromPublisher(
                        withMaxTime(commandStagingCollection.find(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id())))
                                .sort(new Document(FIELD_UPDATED_AT, 1))
                                .limit(limit))
                )
                .map(this::convert)
                .toFlowable(io.reactivex.rxjava3.core.BackpressureStrategy.BUFFER)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CommandStaging> update(CommandStaging commandStaging) {
        CommandStagingMongo commandStagingMongo = convert(commandStaging);
        commandStagingMongo.setUpdatedAt(new Date());

        return Single.fromPublisher(commandStagingCollection.replaceOne(eq(FIELD_ID, commandStaging.getId()), commandStagingMongo))
                .map(result -> convert(commandStagingMongo))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(commandStagingCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private CommandStaging convert(CommandStagingMongo commandStagingMongo) {
        if (commandStagingMongo == null) {
            return null;
        }

        CommandStaging commandStaging = new CommandStaging();
        commandStaging.setId(commandStagingMongo.getId());
        commandStaging.setCommand(commandStagingMongo.getCommand());
        commandStaging.setUserId(commandStagingMongo.getUserId());
        commandStaging.setReferenceType(commandStagingMongo.getReferenceType());
        commandStaging.setReferenceId(commandStagingMongo.getReferenceId());
        commandStaging.setAttempts(commandStagingMongo.getAttempts());
        commandStaging.setTerminalClientIds(commandStagingMongo.getTerminalClientIds() == null ? new ArrayList<>() : new ArrayList<>(commandStagingMongo.getTerminalClientIds()));
        commandStaging.setCreatedAt(commandStagingMongo.getCreatedAt());
        commandStaging.setUpdatedAt(commandStagingMongo.getUpdatedAt());
        return commandStaging;
    }

    private CommandStagingMongo convert(CommandStaging commandStaging) {
        if (commandStaging == null) {
            return null;
        }

        CommandStagingMongo commandStagingMongo = new CommandStagingMongo();
        commandStagingMongo.setId(commandStaging.getId());
        commandStagingMongo.setCommand(commandStaging.getCommand());
        commandStagingMongo.setUserId(commandStaging.getUserId());
        commandStagingMongo.setReferenceType(commandStaging.getReferenceType());
        commandStagingMongo.setReferenceId(commandStaging.getReferenceId());
        commandStagingMongo.setAttempts(commandStaging.getAttempts());
        commandStagingMongo.setTerminalClientIds(commandStaging.getTerminalClientIds());
        commandStagingMongo.setCreatedAt(commandStaging.getCreatedAt());
        commandStagingMongo.setUpdatedAt(commandStaging.getUpdatedAt());
        return commandStagingMongo;
    }
}
