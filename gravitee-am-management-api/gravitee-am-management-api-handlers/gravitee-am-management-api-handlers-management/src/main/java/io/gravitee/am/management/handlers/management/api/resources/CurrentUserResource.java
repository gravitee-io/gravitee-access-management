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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.common.http.MediaType;
import io.reactivex.Single;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"user"})
@Path("/user")
public class CurrentUserResource extends AbstractResource {

    private Logger logger = LoggerFactory.getLogger(CurrentUserResource.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get the current user")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Current user successfully fetched", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(@Suspended final AsyncResponse response) {
        Single<Map<String, Object>> currentUserSource = Single.create(emitter -> {
            try {
                if (isAuthenticated()) {
                    emitter.onSuccess(getAuthenticatedUser().getAdditionalInformation());
                } else {
                    emitter.onError(new IllegalAccessException("Current user is not authenticated"));
                }
            } catch (Exception ex) {
                logger.error("Failed to get user profile information", ex);
                emitter.onError(ex);
            }
        });

        currentUserSource.subscribe(
                result -> response.resume(result),
                error -> response.resume(error));

    }
}
