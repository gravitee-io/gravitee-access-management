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

import java.util.Date;
import java.util.Set;

/**
 * Sort values of date fields are epoch milliseconds.
 */
@Getter
public class AuthorizationDetailApprovalCursorRequest extends CursorRequest {

    public static final String SORT_FIELD_UPDATED_AT = "updatedAt";
    public static final String SORT_FIELD_EXPIRES_AT = "expiresAt";
    public static final String SORT_FIELD_TYPE = "type";
    public static final String SORT_FIELD_STATUS = "status";
    private static final Set<String> SORT_FIELDS = Set.of(SORT_FIELD_UPDATED_AT, SORT_FIELD_EXPIRES_AT, SORT_FIELD_TYPE, SORT_FIELD_STATUS);

    private final String clientId;
    /**
     * Matched ignoring case, where {@code *} matches any characters.
     */
    private final String type;

    public AuthorizationDetailApprovalCursorRequest(String lastSortValue,
                                                    String lastId,
                                                    SortDirection direction,
                                                    String sortField,
                                                    int page,
                                                    String clientId,
                                                    String type) {
        super(lastSortValue, lastId, direction, sortField, page);
        if (!SORT_FIELDS.contains(sortField)) {
            throw new IllegalArgumentException("Invalid sort field: " + sortField);
        }
        if (lastSortValue != null && isDateSort() && !lastSortValue.matches("-?\\d+")) {
            throw new IllegalArgumentException("Invalid cursor sort value: " + lastSortValue);
        }
        this.clientId = clientId;
        this.type = type;
    }

    public static AuthorizationDetailApprovalCursorRequest initialCursor(String sort, String direction, int page, String clientId, String type) {
        return new AuthorizationDetailApprovalCursorRequest(null, null, SortDirection.valueOf(direction.toUpperCase()), sort, page, clientId, type);
    }

    public AuthorizationDetailApprovalCursorRequest next(AuthorizationDetailApproval last) {
        return new AuthorizationDetailApprovalCursorRequest(sortValue(last), last.getId(), getSortDirection(), getSortField(), getPage() + 1, clientId, type);
    }

    public boolean isDateSort() {
        return SORT_FIELD_UPDATED_AT.equals(getSortField()) || SORT_FIELD_EXPIRES_AT.equals(getSortField());
    }

    private String sortValue(AuthorizationDetailApproval approval) {
        return switch (getSortField()) {
            case SORT_FIELD_UPDATED_AT -> epochMillis(approval.getUpdatedAt());
            case SORT_FIELD_EXPIRES_AT -> epochMillis(approval.getExpiresAt());
            case SORT_FIELD_TYPE -> approval.getType();
            default -> approval.getStatus() == null ? null : approval.getStatus().name();
        };
    }

    private static String epochMillis(Date date) {
        return date == null ? null : String.valueOf(date.getTime());
    }
}
