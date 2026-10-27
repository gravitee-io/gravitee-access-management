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
package io.gravitee.am.model.oauth2;

import io.gravitee.am.model.cursor.CursorRequest;
import lombok.Getter;

import java.util.Set;

/**
 * @author GraviteeSource Team
 */
@Getter
public class AuthorizationDetailTypeCursorRequest extends CursorRequest {

    public static final String SORT_FIELD_TYPE = "type";
    public static final String SORT_FIELD_NAME = "name";
    private static final Set<String> SORT_FIELDS = Set.of(SORT_FIELD_TYPE, SORT_FIELD_NAME);

    private final String query;

    public AuthorizationDetailTypeCursorRequest(String lastSortValue,
                                                String lastId,
                                                SortDirection direction,
                                                String sortField,
                                                int page,
                                                String query) {
        super(lastSortValue, lastId, direction, sortField, page);
        if (!SORT_FIELDS.contains(sortField)) {
            throw new IllegalArgumentException("Invalid sort field: " + sortField);
        }
        this.query = query;
    }

    public static AuthorizationDetailTypeCursorRequest initialCursor(String sort, String direction, int page, String query) {
        return new AuthorizationDetailTypeCursorRequest(null, null, SortDirection.valueOf(direction.toUpperCase()), sort, page, query);
    }

    public AuthorizationDetailTypeCursorRequest next(AuthorizationDetailType last) {
        String lastSortValue = SORT_FIELD_NAME.equals(getSortField()) ? last.getName() : last.getType();
        return new AuthorizationDetailTypeCursorRequest(lastSortValue, last.getId(), getSortDirection(), getSortField(), getPage() + 1, query);
    }
}
