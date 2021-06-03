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
import io.gravitee.am.gateway.handler.scim.model.PatchOp;
import io.gravitee.am.gateway.handler.scim.service.GroupService;
import io.gravitee.am.service.exception.GroupNotFoundException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroupEndpoint extends AbstractGroupEndpoint {

    public GroupEndpoint(GroupService groupService, ObjectMapper objectMapper) {
        super(groupService, objectMapper);
    }

    public void get(RoutingContext context) {
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
     */
    public void update(RoutingContext context) {
        try {
            if(context.getBodyAsString() == null) {
                context.fail(new InvalidSyntaxException("Unable to parse body message"));
                return;
            }
            final Group group = Json.decodeValue(context.getBodyAsString(), Group.class);
            final String groupId = context.request().getParam("id");

            // displayName is required
            if (group.getDisplayName() == null || group.getDisplayName().isEmpty()) {
                context.fail(new InvalidValueException("Field [displayName] is required"));
                return;
            }

            // schemas field is REQUIRED and MUST contain valid values and MUST not contain duplicate values
            try {
                checkSchemas(group.getSchemas(), Group.SCHEMAS);
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

    /**
     * HTTP PATCH is an OPTIONAL server function that enables clients to
     * update one or more attributes of a SCIM resource using a sequence of
     * operations to "add", "remove", or "replace" values.  Clients may
     * discover service provider support for PATCH by querying the service
     * provider configuration (see Section 4).
     *
     * The general form of the SCIM PATCH request is based on JSON Patch
     * [RFC6902].  One difference between SCIM PATCH and JSON Patch is that
     * SCIM servers do not support array indexing and do not support
     * [RFC6902] operation types relating to array element manipulation,
     * such as "move".
     *
     * The body of each request MUST contain the "schemas" attribute with
     * the URI value of "urn:ietf:params:scim:api:messages:2.0:PatchOp".
     *
     * The body of an HTTP PATCH request MUST contain the attribute
     * "Operations", whose value is an array of one or more PATCH
     * operations.  Each PATCH operation object MUST have exactly one "op"
     * member, whose value indicates the operation to perform and MAY be one
     * of "add", "remove", or "replace".  The semantics of each operation
     * are defined in the following subsections.
     *
     * See <a href="https://tools.ietf.org/html/rfc7644#section-3.5.2">3.5.2.  Modifying with PATCH</a>
     */
    public void patch(RoutingContext context) {
        try {
            if(context.getBodyAsString() == null) {
                context.fail(new InvalidSyntaxException("Unable to parse body message"));
                return;
            }
            final PatchOp patchOp = Json.decodeValue(context.getBodyAsString(), PatchOp.class);
            final String groupId = context.request().getParam("id");

            // schemas field is REQUIRED and MUST contain valid values and MUST not contain duplicate values
            try {
                checkSchemas(patchOp.getSchemas(), PatchOp.SCHEMAS);
            } catch (Exception ex) {
                context.fail(ex);
                return;
            }

            // check operations
            if (patchOp.getOperations() == null || patchOp.getOperations().isEmpty()) {
                context.fail(new InvalidValueException("Field [Operations] is required"));
                return;
            }

            groupService.patch(groupId, patchOp, location(context.request()))
                    .subscribe(
                            group1 -> context.response()
                                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                    .putHeader(HttpHeaders.LOCATION, group1.getMeta().getLocation())
                                    .end(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(group1)),
                            context::fail);
        } catch (DecodeException ex) {
            context.fail(new InvalidSyntaxException("Unable to parse body message", ex));
        }
    }


    /**
     * Clients request resource removal via DELETE.  Service providers MAY
     *    choose not to permanently delete the resource but MUST return a 404
     *    (Not Found) error code for all operations associated with the
     *    previously deleted resource.  Service providers MUST omit the
     *    resource from future query results.  In addition, the service
     *    provider SHOULD NOT consider the deleted resource in conflict
     *    calculation.  For example, if a User resource is deleted, a CREATE
     *    request for a User resource with the same userName as the previously
     *    deleted resource SHOULD NOT fail with a 409 error due to userName
     *    conflict.
     *
     * In response to a successful DELETE, the server SHALL return a
     *    successful HTTP status code 204 (No Content).
     *
     * See <a href="https://tools.ietf.org/html/rfc7644#section-3.6>3.6. Deleting Resources</a>
     */
    public void delete(RoutingContext context) {
        final String groupId = context.request().getParam("id");
        groupService.delete(groupId)
                .subscribe(
                        () -> context.response().setStatusCode(204).end(),
                        error -> context.fail(error));
    }
}
