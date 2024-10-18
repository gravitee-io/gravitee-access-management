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

import lombok.Getter;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Getter
public class BulkResponse<T> {
    private final List<BulkOperationResult<T>> results;
    private final boolean allSuccessful;

    public BulkResponse(List<BulkOperationResult<T>> results) {
        if (CollectionUtils.isEmpty(results)) {
            throw new IllegalArgumentException("BulkResponse must contain at least 1 result");
        }
        this.results = results.stream().sorted(BulkOperationResult.byIndex()).toList();
        this.allSuccessful = results.stream().allMatch(BulkOperationResult::success);
    }
}
