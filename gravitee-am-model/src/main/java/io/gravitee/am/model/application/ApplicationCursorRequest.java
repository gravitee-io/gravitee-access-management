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
package io.gravitee.am.model.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.cursor.CursorRequest;
import lombok.Getter;

import java.util.List;

@Getter
public class ApplicationCursorRequest extends CursorRequest {
    private final String query;
    private final Boolean enabled;
    private final List<ApplicationType> types;

    public ApplicationCursorRequest(String lastSortValue,
                                    String lastId,
                                    SortDirection direction,
                                    String sortField,
                                    int page,
                                    String query,
                                    Boolean enabled,
                                    List<ApplicationType> types) {
        super(lastSortValue, lastId, direction, sortField, page);
        this.query = query;
        this.enabled = enabled;
        this.types = types;
    }

    public static ApplicationCursorRequest initialCursor(String sort, String direction, int page, String query, Boolean enabled, List<ApplicationType> types) {
        return new ApplicationCursorRequest(null, null, SortDirection.valueOf(direction.toUpperCase()), sort, page, query, enabled, types);
    }

}
