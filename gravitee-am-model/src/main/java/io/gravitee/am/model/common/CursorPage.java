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
package io.gravitee.am.model.common;

import java.util.Collection;
import java.util.List;

/**
 * Cursor-based pagination response. Unlike {@link Page}, this does not include a total count
 * (which requires an expensive full scan). Instead it provides an opaque cursor for fetching
 * the next page and a {@code hasNext} flag.
 *
 * @param <T> the type of items in the page
 */
public class CursorPage<T> {
    private final List<T> data;
    private final String nextCursor;
    private final boolean hasNext;
    private final Long totalCount;

    public CursorPage(Collection<T> data, String nextCursor, boolean hasNext) {
        this(data, nextCursor, hasNext, null);
    }

    public CursorPage(Collection<T> data, String nextCursor, boolean hasNext, Long totalCount) {
        this.data = data != null ? List.copyOf(data) : List.of();
        this.nextCursor = nextCursor;
        this.hasNext = hasNext;
        this.totalCount = totalCount;
    }

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false, null);
    }

    public CursorPage<T> withTotalCount(Long totalCount) {
        return new CursorPage<>(this.data, this.nextCursor, this.hasNext, totalCount);
    }

    public List<T> getData() {
        return data;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public Long getTotalCount() {
        return totalCount;
    }
}
