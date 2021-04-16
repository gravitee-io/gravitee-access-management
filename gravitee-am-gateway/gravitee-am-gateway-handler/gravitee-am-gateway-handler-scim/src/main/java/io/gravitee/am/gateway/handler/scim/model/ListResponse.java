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
package io.gravitee.am.gateway.handler.scim.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * See <a href="https://tools.ietf.org/html/rfc7644#section-3.4.2">3.4.2. Query Resources</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ListResponse<T extends Resource> extends Resource {

    private static final List<String> SCHEMAS = Collections.singletonList("urn:ietf:params:scim:api:messages:2.0:ListResponse");

    /**
     * The total number of results returned by the list or
     *       query operation.  The value may be larger than the number of
     *       resources returned, such as when returning a single page (see
     *       Section 3.4.2.4) of results where multiple pages are available.
     *       REQUIRED.
     */
    private long totalResults;

    /**
     * A multi-valued list of complex objects containing the
     *       requested resources.  This MAY be a subset of the full set of
     *       resources if pagination (Section 3.4.2.4) is requested.  REQUIRED
     *       if "totalResults" is non-zero.
     */
    @JsonProperty("Resources")
    private List<T> resources;

    /**
     * The 1-based index of the first result in the current set
     *       of list results.  REQUIRED when partial results are returned due
     *       to pagination.
     */
    private Integer startIndex;

    /**
     * The number of resources returned in a list response
     *       page.  REQUIRED when partial results are returned due to
     *       pagination.
     */
    private Integer itemsPerPage;

    public ListResponse() {}

    public ListResponse(List<T> resources, Integer startIndex, long totalResults, Integer itemsPerPage) {
        this.resources = resources;
        this.startIndex = startIndex;
        this.totalResults = totalResults;
        this.itemsPerPage = itemsPerPage;
    }

    public List<String> getSchemas() {
        return SCHEMAS;
    }

    public long getTotalResults() {
        return totalResults;
    }

    public void setTotalResults(long totalResults) {
        this.totalResults = totalResults;
    }

    public List<T> getResources() {
        return resources;
    }

    public void setResources(List<T> resources) {
        this.resources = resources;
    }

    public Integer getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(Integer startIndex) {
        this.startIndex = startIndex;
    }

    public Integer getItemsPerPage() {
        return itemsPerPage;
    }

    public void setItemsPerPage(Integer itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
    }
}
