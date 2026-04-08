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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;
import java.util.List;

/**
 * Cursor-based pagination response. Provides an opaque cursor for fetching the next page.
 * {@code hasNext} is derived from {@code nextCursor} — they cannot be contradictory.
 *
 * @param <T> the type of items in the page
 */
public class CursorPage<T> {
    private final List<T> data;
    private final String nextCursor;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Long totalCount;

    public CursorPage(Collection<T> data, String nextCursor) {
        this(data, nextCursor, null);
    }

    public CursorPage(Collection<T> data, String nextCursor, Long totalCount) {
        this.data = data != null ? List.copyOf(data) : List.of();
        this.nextCursor = nextCursor;
        this.totalCount = totalCount;
    }

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, null);
    }

    public CursorPage<T> withTotalCount(Long totalCount) {
        return new CursorPage<>(this.data, this.nextCursor, totalCount);
    }

    public List<T> getData() {
        return data;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    @JsonProperty("hasNext")
    public boolean isHasNext() {
        return nextCursor != null;
    }

    public Long getTotalCount() {
        return totalCount;
    }
}
