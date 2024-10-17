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

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class BulkResponse {
    private final List<BulkOperationResult> results;
    @JsonIgnore
    private final Response.Status status;

    public BulkResponse(List<BulkOperationResult> results) {
        if (CollectionUtils.isEmpty(results)) {
            throw new IllegalArgumentException("BulkResponse must contain at least 1 result");
        }
        this.results = results.stream().sorted(BulkOperationResult.byIndex()).toList();
        var statuses = results.stream().map(BulkOperationResult::httpStatus).collect(Collectors.toSet());
        if (statuses.size() == 1) {
            // all responses have the same status - let's just use this
            this.status = statuses.iterator().next();
        } else if (statuses.stream().allMatch(s -> s.getStatusCode() >= 500)) {
            // we got various statuses, but all are some server error
            this.status = Response.Status.INTERNAL_SERVER_ERROR;
        } else if (statuses.stream().allMatch(s -> s.getStatusCode() >= 400)) {
            // we got various statuses, but all are some errors, and there's at least one client error
            this.status = Response.Status.BAD_REQUEST;
        } else {
            // any other mix - caller should inspect the response
            this.status = Response.Status.OK;
        }
    }
}
