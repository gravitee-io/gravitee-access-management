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
import io.gravitee.am.gateway.handler.scim.exception.InvalidSyntaxException;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.model.Group;
import io.gravitee.am.gateway.handler.scim.service.GroupService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 *  As the operation's intent is to replace all attributes, SCIM clients
 *    MAY send all attributes, regardless of each attribute's mutability.
 *    The server will apply attribute-by-attribute replacements according
 *    to the following attribute mutability rules:
 *
 *    readWrite, writeOnly  Any values provided SHALL replace the existing
 *       attribute values.
 *
 *       Attributes whose mutability is "readWrite" that are omitted from
 *       the request body MAY be assumed to be not asserted by the client.
 *       The service provider MAY assume that any existing values are to be
 *       cleared, or the service provider MAY assign a default value to the
 *       final resource representation.  Service providers MAY take into
 *       account whether or not a client has access to, or understands, all
 *       of the resource's attributes when deciding whether non-asserted
 *       attributes SHALL be removed or defaulted.  Clients that want to
 *       override a server's defaults MAY specify "null" for a
 *       single-valued attribute, or an empty array "[]" for a multi-valued
 *       attribute, to clear all values.
 *
 *    immutable  If one or more values are already set for the attribute,
 *       the input value(s) MUST match, or HTTP status code 400 SHOULD be
 *       returned with a "scimType" error code of "mutability".  If the
 *       service provider has no existing values, the new value(s) SHALL be
 *       applied.
 *
 *    readOnly  Any values provided SHALL be ignored.
 *
 *    If an attribute is "required", clients MUST specify the attribute in
 *    the PUT request.
 *
 * See <a href="https://tools.ietf.org/html/rfc7644#section-3.5.1">3.5.1. Replacing with PUT</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UpdateGroupEndpointHandler extends AbstractGroupEndpointHandler {

    private GroupService groupService;
    private ObjectMapper objectMapper;

    public UpdateGroupEndpointHandler(GroupService groupService) {
        this.groupService = groupService;
    }

    @Override
    public void handle(RoutingContext context) {
        try {
            final Group group = Json.decodeValue(context.getBodyAsString(), Group.class);
            final String groupId = context.request().getParam("id");

            // displayName is required
            if (group.getDisplayName() == null || group.getDisplayName().isEmpty()) {
                context.fail(new InvalidValueException("Field [displayName] is required"));
                return;
            }

            // schemas field is REQUIRED and MUST contain valid values and MUST not contain duplicate values
            try {
                checkSchemas(group.getSchemas());
            } catch (Exception ex) {
                context.fail(ex);
                return;
            }

            groupService.update(groupId, group, location(context.request()))
                    .subscribe(
                            group1 -> context.response()
                                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                    .putHeader(HttpHeaders.LOCATION, group1.getMeta().getLocation())
                                    .end(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(group1)),
                            error -> context.fail(error));
        } catch (DecodeException ex) {
            context.fail(new InvalidSyntaxException("Unable to parse body message", ex));
        }
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static UpdateGroupEndpointHandler create(GroupService groupService) {
        return new UpdateGroupEndpointHandler(groupService);
    }
}
