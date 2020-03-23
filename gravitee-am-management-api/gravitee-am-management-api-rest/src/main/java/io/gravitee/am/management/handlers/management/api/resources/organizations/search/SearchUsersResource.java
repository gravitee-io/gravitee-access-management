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
package io.gravitee.am.management.handlers.management.api.resources.organizations.search;

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.UserService;
import io.gravitee.common.http.MediaType;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
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
public class SearchUsersResource extends AbstractResource {

    private static final int MAX_USERS_SIZE_PER_PAGE = 30;
    private static final String MAX_USERS_SIZE_PER_PAGE_STRING = "30";

    @Autowired
    private UserService userService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Search users of the organization",
            notes = "User must have the ORGANIZATION[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Users search result", response = User.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @QueryParam("q") String query,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue(MAX_USERS_SIZE_PER_PAGE_STRING) int size,
            @Suspended final AsyncResponse response) {

        Single<Page<User>> usersPageObs = null;

        if (query != null) {
            usersPageObs = userService.search(ReferenceType.ORGANIZATION, organizationId, query, page, Integer.min(size, MAX_USERS_SIZE_PER_PAGE));
        } else {
            usersPageObs = userService.findAll(ReferenceType.ORGANIZATION, organizationId, page, Integer.min(size, MAX_USERS_SIZE_PER_PAGE));
        }

        // We only need to make sure current user can access the organization.
        // We don't want to use ORGANIZATION_USER[READ] permission which give ability to view all information about all organization users.
        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION, Acl.READ)
                .andThen(usersPageObs.flatMap(pagedUsers -> Observable.fromIterable(pagedUsers.getData()).toSortedList(Comparator.comparing(User::getUsername))
                        .map(users -> new Page<>(users, pagedUsers.getCurrentPage(), pagedUsers.getTotalCount()))))
                .subscribe(response::resume, response::resume);
    }
}
