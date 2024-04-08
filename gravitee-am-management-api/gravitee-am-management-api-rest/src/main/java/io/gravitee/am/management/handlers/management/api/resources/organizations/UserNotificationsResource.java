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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.UserNotificationService;
import io.gravitee.am.model.notification.UserNotification;
import io.gravitee.am.model.notification.UserNotificationContent;
import io.gravitee.am.model.notification.UserNotificationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tags({@Tag(name= "user notifications")})
public class UserNotificationsResource extends AbstractResource {
    private final Logger logger = LoggerFactory.getLogger(UserNotificationsResource.class);

    @Autowired
    private UserNotificationService notificationService;

    private ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List notifications received by the current user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user notifications successfully fetched",
                    content = @Content(mediaType =  "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = UserNotificationContent.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void listNotifications(@Suspended final AsyncResponse response) {
        // All users have rights to read notifications
        if (! isAuthenticated()) {
            response.resume(new ForbiddenException());
            return;
        }

        final User authenticatedUser = getAuthenticatedUser();
        notificationService.listAllNotifications(authenticatedUser, UserNotificationStatus.UNREAD)
                .map(this::filterNotificationData)
                .filter(notif -> notif.getMessage() != null && notif.getTitle() != null)
                .toList()
                .subscribe(response::resume, response::resume);
    }

    private UserNotificationContent filterNotificationData(UserNotification notification) {
        final UserNotificationContent filteredNotification = new UserNotificationContent(notification);
        try {
            Map<String, String> content = mapper.readValue(notification.getMessage(), Map.class);
            filteredNotification.setTitle(content.get("title"));
            filteredNotification.setMessage(content.get("message"));
        } catch (JsonProcessingException e) {
            logger.warn("Unable to read message for user notification '{}' : {}", notification.getId(), e.getMessage());
        }
        return filteredNotification;
    }

    @POST
    @Path("/{notificationId}/acknowledge")
    @Operation(summary = "Mark User notification as read")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User notification has been marked as read"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void markAsRead(@PathParam("notificationId") String notificationId, @Suspended final AsyncResponse response) {
        // All users have rights to read notifications
        if (! isAuthenticated()) {
            response.resume(new ForbiddenException());
            return;
        }

        final User authenticatedUser = getAuthenticatedUser();
        notificationService.markAsRead(authenticatedUser, notificationId)
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
