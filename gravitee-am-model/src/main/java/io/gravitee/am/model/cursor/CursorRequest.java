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
package io.gravitee.am.model.cursor;

import lombok.Getter;

@Getter
public class CursorRequest {

    private final String lastId;

    private final String lastSortValue;
    private final SortDirection sortDirection;
    private final String sortField;
    private final int page;

    public CursorRequest(String lastSortValue,
                         String lastId,
                         SortDirection direction,
                         String sortField,
                         int page) {
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        this.lastSortValue = lastSortValue;
        this.lastId = lastId;
        this.sortDirection = direction;
        this.sortField = sortField;
        this.page = page;
    }

    public boolean isFirstPage() {
        return lastSortValue == null && lastId == null;
    }

    public enum SortDirection {
        ASC, DESC;

        public boolean isAscending() {
            return this == ASC;
        }

        public int toInt() {
            if (isAscending()) {
                return 1;
            } else {
                return -1;
            }
        }
    }

}