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
package io.gravitee.am.management.handlers.management.api.sort;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SortParam {
    private String sortBy;
    private boolean ascending;

    public SortParam() {
    }

    public SortParam(String sortBy,
                     boolean ascending) {
        this.sortBy = sortBy;
        this.ascending = ascending;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return sortBy == null || sortBy.isEmpty();
    }

    public static SortParam empty() {
        return new SortParam(null, false);
    }
}
