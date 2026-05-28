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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CursorPage<T, C extends CursorRequest> {
    private final List<T> data;
    private final C nextCursor;
    private final Long totalCount;


    public CursorPage(Collection<T> data, C nextCursor, Long totalCount) {
        this.data = data != null ? new ArrayList<>(data) : new ArrayList<>();
        this.nextCursor = nextCursor;
        this.totalCount = totalCount;
    }

    public static <T, C extends CursorRequest> CursorPage<T, C> empty() {
        return new CursorPage<>(List.of(), null, null);
    }

    public List<T> getData() {
        return data;
    }

    public C getNextCursor() {
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