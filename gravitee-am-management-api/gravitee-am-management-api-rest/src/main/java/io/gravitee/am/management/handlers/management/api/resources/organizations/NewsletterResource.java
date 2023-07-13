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
package io.gravitee.am.management.handlers.management.api.resources.organizations;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.model.EmailValue;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.NewsletterService;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.UserService;
import io.swagger.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines the REST resources to manage Newsletter.
 *
 * @author Titouan COMPIEGGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Newsletter"})
public class NewsletterResource extends AbstractResource {

    public static final Logger LOGGER = LoggerFactory.getLogger(NewsletterResource.class);

    @Autowired
    private NewsletterService newsletterService;

    @Autowired
    private OrganizationUserService userService;

    @POST
    @Path("/_subscribe")
    @ApiOperation(value = "Subscribe to the newsletter the authenticated user")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Updated user", response = User.class),
            @ApiResponse(code = 400, message = "Invalid user profile"),
            @ApiResponse(code = 404, message = "User not found"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void subscribeNewsletter(@ApiParam(name = "email", required = true) @Valid @NotNull final EmailValue emailValue,
                                    @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        // Get the organization the current user is logged on.
        String organizationId = (String) authenticatedUser.getAdditionalInformation().getOrDefault(Claims.organization, Organization.DEFAULT);

        userService.findById(ReferenceType.ORGANIZATION, organizationId, authenticatedUser.getId())
                .flatMap(user -> {
                    user.setEmail(emailValue.getEmail());
                    user.setNewsletter(true);
                    return userService.update(user);
                })
                .doOnSuccess(endUser -> {
                    Map<String, Object> object = new HashMap<>();
                    object.put("email", endUser.getEmail());
                    newsletterService.subscribe(object);
                })
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("/taglines")
    @ApiOperation(value = "Get taglines to display in the newsletter")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Retrieved taglines", response = List.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void getTaglines(@Suspended final AsyncResponse response) {

        newsletterService.getTaglines()
            .subscribe(
                    taglines -> response.resume(taglines),
                    error -> {
                        LOGGER.error("An error has occurred when reading the newsletter taglines response", error);
                        response.resume(Collections.emptyList());
                    });
    }
}
