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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import jakarta.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.With;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@ToString
public class BulkOperationResult<T> {

    private static final int NO_INDEX = Integer.MIN_VALUE;

    @With
    private int index;

    private Response.Status httpStatus;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Getter
    private T body;
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object errorDetails;

    @JsonProperty
    public int index() {
        return index;
    }

    @JsonSerialize(using = ResponseStatusIntSerializer.class)
    public Response.Status httpStatus() {
        return httpStatus;
    }

    @JsonProperty("success")
    public boolean success() {
        return httpStatus().getStatusCode() < 400;
    }

    static <T> Comparator<BulkOperationResult<T>> byIndex() {
        return Comparator.comparing(BulkOperationResult::index);
    }

    public static <R> BulkOperationResult<R> success(Response.Status status, R body) {
        return new BulkOperationResult<>(NO_INDEX, status, body, null);
    }

    public static <R> BulkOperationResult<R> ok(R body) {
        return success(Response.Status.OK, body);
    }

    public static <R> BulkOperationResult<R> created(R body) {
        return success(Response.Status.CREATED, body);
    }

    public static <R> BulkOperationResult<R> error(Response.Status statusCode, Throwable exception) {
        var details = exception == null
                ? "unknown error"
                : Map.of("error", exception.getClass().getSimpleName(), "message", exception.getMessage());
        return new BulkOperationResult<>(NO_INDEX, statusCode, null, details);
    }

    public static <R> BulkOperationResult<R> error(Response.Status statusCode) {
        return error(statusCode, null);
    }

    static class ResponseStatusIntSerializer extends StdSerializer<Response.Status> {

        protected ResponseStatusIntSerializer() {
            super(Response.Status.class);
        }

        @Override
        public void serialize(Response.Status value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeNumber(value.getStatusCode());
        }
    }

}
