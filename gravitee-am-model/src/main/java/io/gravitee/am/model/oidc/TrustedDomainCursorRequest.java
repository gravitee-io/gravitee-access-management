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
package io.gravitee.am.model.oidc;

import io.gravitee.am.model.cursor.CursorRequest;
import lombok.Getter;

@Getter
public class TrustedDomainCursorRequest extends CursorRequest {
    private final TrustedDomainUsage usage;

    public TrustedDomainCursorRequest(String lastSortValue,
                                      String lastId,
                                      SortDirection direction,
                                      String sortField,
                                      int page,
                                      TrustedDomainUsage usage) {
        super(lastSortValue, lastId, direction, sortField, page);
        this.usage = usage;
    }

    public static TrustedDomainCursorRequest initialCursor(String sort, String direction, int page, TrustedDomainUsage usage) {
        return new TrustedDomainCursorRequest(null, null, SortDirection.valueOf(direction.toUpperCase()), sort, page, usage);
    }
}
