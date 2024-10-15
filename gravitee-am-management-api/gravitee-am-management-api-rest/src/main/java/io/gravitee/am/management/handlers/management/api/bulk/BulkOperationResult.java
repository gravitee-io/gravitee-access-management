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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import jakarta.ws.rs.core.Response;
import lombok.With;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;

public sealed interface BulkOperationResult {

    int NO_INDEX = Integer.MIN_VALUE;

    int index();

    @JsonSerialize(using = ResponseStatusIntSerializer.class)
    Response.Status httpStatus();

    @JsonProperty("success")
    default boolean success() {
        return httpStatus().getStatusCode() < 400;
    }

    BulkOperationResult withIndex(int index);


    record Success<T>(@With int index, Response.Status httpStatus, T response) implements BulkOperationResult {

    }

    record Error(@With int index, Response.Status httpStatus,
                 Object errorDetails) implements BulkOperationResult {

    }


    static Comparator<BulkOperationResult> byIndex() {
        return Comparator.comparing(BulkOperationResult::index);
    }

    static <R> BulkOperationResult success(Response.Status status, R body) {
        return new Success<>(NO_INDEX, status, body);
    }

    static <R> BulkOperationResult ok(R body) {
        return new Success<>(NO_INDEX, Response.Status.OK, body);
    }

    static <R> BulkOperationResult created(R body) {
        return new Success<>(NO_INDEX, Response.Status.CREATED, body);
    }

    static BulkOperationResult error(Response.Status statusCode) {
        return new Error(NO_INDEX, statusCode, null);
    }

    static BulkOperationResult error(Response.Status statusCode, Exception exception) {
        return new Error(NO_INDEX, statusCode, Map.of("error", exception.getClass().getSimpleName(), "error_details", exception.getMessage()));
    }

    static BulkOperationResult error(Response.Status statusCode, Object errorDetails) {
        return new Error(NO_INDEX, statusCode, errorDetails);
    }


    class ResponseStatusIntSerializer extends StdSerializer<Response.Status> {

        protected ResponseStatusIntSerializer() {
            super(Response.Status.class);
        }

        @Override
        public void serialize(Response.Status value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeNumber(value.getStatusCode());
        }
    }

}
