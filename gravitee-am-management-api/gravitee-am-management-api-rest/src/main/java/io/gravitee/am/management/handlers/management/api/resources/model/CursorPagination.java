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
package io.gravitee.am.management.handlers.management.api.resources.model;

import io.gravitee.am.model.cursor.CursorRequest;
import jakarta.ws.rs.core.UriInfo;

import java.util.function.Function;
import java.util.function.Supplier;

public final class CursorPagination {

    public static final int DEFAULT_LIMIT = 50;
    public static final String DEFAULT_LIMIT_STRING = "" + DEFAULT_LIMIT;
    public static final int MAX_LIMIT = 100;

    public static final String PARAM_LIMIT = "limit";
    public static final String PARAM_SORT = "sort";
    public static final String PARAM_DIR = "dir";
    public static final String PARAM_PAGE = "page";
    public static final String PARAM_CURSOR = "cursor";

    public static final String LIMIT_DESCRIPTION = "Page size, at most " + MAX_LIMIT + "; a larger value is reduced to " + MAX_LIMIT;
    public static final String PAGE_DESCRIPTION = "Page number, starting at 0. Without a cursor, the response starts at this page.";
    public static final String CURSOR_DESCRIPTION = "Position to continue from, as given in a previous page's nextCursor";

    private CursorPagination() {
    }

    public static int pageSize(int limit) {
        return Math.min(limit, MAX_LIMIT);
    }

    public static <C extends CursorRequest> C cursorRequest(String cursorEncoded, String sort, String direction,
                                                            Supplier<C> firstPage,
                                                            Function<CursorApiRequest, C> fromCursor) {
        if (cursorEncoded == null) {
            return firstPage.get();
        }
        return fromCursor.apply(CursorApiRequest.decode(cursorEncoded, sort, direction));
    }

    public static String nextCursorPath(CursorRequest nextCursor, UriInfo uriInfo, String basePath) {
        if (nextCursor == null) {
            return null;
        }
        String query = uriInfo.getRequestUriBuilder()
                .replaceQueryParam(PARAM_CURSOR, CursorApiRequest.of(nextCursor).encode())
                .replaceQueryParam(PARAM_PAGE, nextCursor.getPage())
                .build()
                .getRawQuery();
        return basePath + "?" + query;
    }
}
