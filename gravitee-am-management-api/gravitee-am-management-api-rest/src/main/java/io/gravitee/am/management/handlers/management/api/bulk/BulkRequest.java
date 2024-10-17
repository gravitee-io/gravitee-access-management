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

import io.gravitee.am.common.utils.Indexed;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.function.Function;

public record BulkRequest<T>(List<T> items) {

    /**
     * Apply the processor to each individual item in this request. There's no guarantees about ordering or concurrency
     * of processing; the response is guaranteed to have results of processing individual items in the input order
     */
    public Single<Response> processOneByOne(Function<T, Single<BulkOperationResult>> processor) {
        if (CollectionUtils.isEmpty(items)) {
            return Single.just(Response.noContent().build());
        }
        return Indexed.toIndexedFlowable(items)
                .flatMapSingle(indexed -> processor.apply(indexed.value())
                        .map(result -> result.withIndex(indexed.index())))
                .toList()
                .map(this::makeResponse);
    }

    private Response makeResponse(List<BulkOperationResult> bulkOperationResults) {
        var bulkResponse = new BulkResponse(bulkOperationResults);
        return Response.status(bulkResponse.getStatus()).entity(bulkResponse).build();
    }
}
