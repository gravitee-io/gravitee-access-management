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
package io.gravitee.am.management.handlers.management.api.resources.platform.search;

import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.UserService;
import io.gravitee.common.http.MediaType;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;
import java.util.Comparator;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 *
 * TODO : user identity providers lookup method instead
 */
public class SearchUsersResource {

    private static final int MAX_USERS_SIZE_PER_PAGE = 30;
    private static final String MAX_USERS_SIZE_PER_PAGE_STRING = "30";

    @Autowired
    private UserService userService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Search users of the platform")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Users search result", response = User.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(@QueryParam("q") String query,
                     @QueryParam("page") @DefaultValue("0") int page,
                     @QueryParam("size") @DefaultValue(MAX_USERS_SIZE_PER_PAGE_STRING) int size,
                     @Suspended final AsyncResponse response) {

        String organizationId = "DEFAULT";

        Single<Page<User>> usersPageObs = null;

        if (query != null) {
            usersPageObs = userService.search(ReferenceType.ORGANIZATION, organizationId, query, page, Integer.min(size, MAX_USERS_SIZE_PER_PAGE));
        } else {
            usersPageObs = userService.findAll(ReferenceType.ORGANIZATION, organizationId, page, Integer.min(size, MAX_USERS_SIZE_PER_PAGE));
        }

        usersPageObs.flatMap(pagedUsers -> Observable.fromIterable(pagedUsers.getData()).toSortedList(Comparator.comparing(User::getUsername))
                .map(users -> new Page<>(users, pagedUsers.getCurrentPage(), pagedUsers.getTotalCount())))
                .map(users -> Response.ok(users).build())
                .subscribe(response::resume, response::resume);
    }
}
