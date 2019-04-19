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
package io.gravitee.am.gateway.handler.scim.resources.groups;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.scim.service.GroupService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ListGroupEndpointHandler extends AbstractGroupEndpointHandler{

    private static final int MAX_ITEMS_PER_PAGE = 100;
    private static final int DEFAULT_START_INDEX = 1;
    private GroupService groupService;
    private ObjectMapper objectMapper;

    public ListGroupEndpointHandler(GroupService groupService) {
        this.groupService = groupService;
    }

    @Override
    public void handle(RoutingContext context) {
        // Pagination (https://tools.ietf.org/html/rfc7644#section-3.4.2.4)
        Integer page = DEFAULT_START_INDEX;
        Integer size = MAX_ITEMS_PER_PAGE;


        // The 1-based index of the first query result.
        // A value less than 1 SHALL be interpreted as 1.
        try {
            final String startIndex = context.request().getParam("startIndex");
            page = Integer.valueOf(startIndex);
        } catch (Exception ex) {
        }
        // Non-negative integer. Specifies the desired  results per page, e.g., 10.
        // A negative value SHALL be interpreted as "0".
        // A value of "0"  indicates that no resource results are to be returned except for "totalResults".
        try {
            final String count = context.request().getParam("count");
            size = Integer.min(Integer.valueOf(count), MAX_ITEMS_PER_PAGE);
        } catch (Exception ex) {

        }

        // group service use 0-based index
        groupService.list(page - 1, size, location(context.request()))
                .subscribe(
                        groups -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .end(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(groups)),
                        error -> context.fail(error));
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static ListGroupEndpointHandler create(GroupService groupService) {
        return new ListGroupEndpointHandler(groupService);
    }
}
