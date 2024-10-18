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
package io.gravitee.am.management.handlers.management.api.bulk;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.common.utils.Indexed;
import io.gravitee.am.model.Acl;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.function.Function;

/**
 * Represents a create, update or delete operation on multiple entities.
 *
 * @param <T> type of item included
 */
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public class BulkRequest<T> {
    @RequiredArgsConstructor
    @Getter
    @Accessors(fluent = true)
    public enum Action {
        CREATE(Acl.CREATE), UPDATE(Acl.UPDATE), DELETE(Acl.DELETE);
        private final Acl requiredAcl;
    }

    @JsonProperty("action")
    @NotNull
    private final Action action;
    @JsonProperty("items")
    private final List<T> items;

    protected BulkRequest(Action action) {
        this(action, List.of());
    }

    /**
     * Apply the processor to each individual item in this request. There's no guarantees about ordering or concurrency
     * of processing; the response is guaranteed to have results of processing individual items in the input order.
     */
    public final <R> Single<Response> processOneByOne(Function<T, Single<BulkOperationResult<R>>> processor) {
        if (CollectionUtils.isEmpty(items())) {
            return Single.just(Response.status(Response.Status.BAD_REQUEST).entity("received a bulk request without any items").build());
        }
        return Indexed.toIndexedFlowable(items())
                .flatMapSingle(indexed -> processor.apply(indexed.value())
                        .map(result -> result.withIndex(indexed.index())))
                .toList()
                .map(this::makeResponse);
    }

    private <R> Response makeResponse(List<BulkOperationResult<R>> bulkOperationResults) {
        var bulkResponse = new BulkResponse<>(bulkOperationResults);
        return Response.status(Response.Status.OK).entity(bulkResponse).build();
    }

    /**
     * A BulkRequest that defers deserialization of items until the endpoint provides the actual type to use.
     * This allows defining a single /bulk endpoint that accepts different arguments for different actions. E.g.
     * a CREATE action might use a complex DTO, while a simple list of ids will be enough for a bulk DELETE.
     */
    public static final class Generic extends BulkRequest<ObjectNode> {

        @JsonCreator
        public Generic(@JsonProperty("items") List<ObjectNode> items, @JsonProperty("action") Action action) {
            super(action, items);
        }

        /**
         * Convert this generic request to a concrete one, mapping each item to {@code itemType} using the provided mapper
         *
         * @return this request with items mapped to the expected type
         */
        public <T> BulkRequest<T> readItemsAs(Class<T> itemType, ObjectMapper mapper) {
            return readItemsAs(mapper, mapper.constructType(itemType));
        }

        /**
         * Similar to {@link #readItemsAs(Class, ObjectMapper)} but handles items with generic types.
         *
         * @return this request with items mapped to the expected type
         */
        public <T> BulkRequest<T> readItemsAs(TypeReference<T> itemType, ObjectMapper mapper) {
            return readItemsAs(mapper, mapper.constructType(itemType));
        }


        @SuppressWarnings("unchecked") // always called with a JavaType representing T
        private <T> BulkRequest<T> readItemsAs(ObjectMapper mapper, JavaType type) {
            return new BulkRequest<>(action(), items().stream().map(x -> {
                try {
                    return (T) mapper.treeToValue(x, type);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }).toList());
        }

        /**
         * Convenience overload for converting & processing items in one go
         *
         * @param <S> input item type
         * @param <R> output item type
         */
        public <S, R> Single<Response> processOneByOne(Class<S> itemType, ObjectMapper mapper, Function<S, Single<BulkOperationResult<R>>> itemProcessor) {
            return readItemsAs(itemType, mapper).processOneByOne(itemProcessor);
        }
    }
}
