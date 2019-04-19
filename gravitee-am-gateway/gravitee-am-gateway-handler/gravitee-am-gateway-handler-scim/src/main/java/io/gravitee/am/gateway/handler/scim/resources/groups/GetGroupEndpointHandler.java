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
import io.gravitee.am.service.exception.GroupNotFoundException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.reactivex.ext.web.RoutingContext;


/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GetGroupEndpointHandler extends AbstractGroupEndpointHandler {

    private GroupService groupService;
    private ObjectMapper objectMapper;

    public GetGroupEndpointHandler(GroupService groupService) {
        this.groupService = groupService;
    }

    @Override
    public void handle(RoutingContext context) {
        final String groupId = context.request().getParam("id");
        groupService
                .get(groupId, location(context.request()))
                .subscribe(
                        group -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .putHeader(HttpHeaders.LOCATION, group.getMeta().getLocation())
                                .end(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(group)),
                        error -> context.fail(error),
                        () -> context.fail(new GroupNotFoundException(groupId)));

    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static GetGroupEndpointHandler create(GroupService groupService) {
        return new GetGroupEndpointHandler(groupService);
    }
}
