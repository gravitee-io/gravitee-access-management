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

package io.gravitee.am.repository.jdbc.management.api;


import org.springframework.data.domain.AbstractPageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OffsetPageRequest extends AbstractPageRequest {
    private final int offset;
    private final Sort sort;

    public OffsetPageRequest(int offset, int size) {
        this(offset, size, Sort.unsorted());
    }

    public OffsetPageRequest(int offset, int size, Sort sort) {
        super(size > 0 ? (offset/size) : 0, size);
        this.offset = offset;
        this.sort = sort;
    }

    @Override
    public Pageable next() {
        return new OffsetPageRequest(offset + getPageSize(), getPageSize(), this.sort);
    }

    @Override
    public Pageable previous() {
        return new OffsetPageRequest(Integer.max(0, offset - getPageSize()), getPageSize(), this.sort);
    }

    @Override
    public Pageable first() {
        return new OffsetPageRequest(0, getPageSize(), this.sort);
    }

    @Override
    public Sort getSort() {
        return this.sort;
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetPageRequest(pageNumber * getPageSize(), getPageSize(), this.sort);
    }
}
