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
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Page<T> {
    private final List<T> data;
    private final int currentPage;
    private final long totalCount;

    public Page(){
        this(List.of(), 0, 0);
    }

    public Page(Collection<T> data, int currentPage, long totalCount) {
        this.data = data != null ? List.copyOf(data) : List.of();
        this.currentPage = currentPage;
        this.totalCount = totalCount;
    }

    public Collection<T> getData() {
        return data;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public static int pageFromOffset(int offset, int size) {
        return size > 0 ? (offset/size) : 0;
    }
}
