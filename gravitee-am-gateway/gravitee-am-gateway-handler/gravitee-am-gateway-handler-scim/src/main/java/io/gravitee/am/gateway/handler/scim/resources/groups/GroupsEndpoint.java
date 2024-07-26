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
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.scim.filter.Filter;
import io.gravitee.am.common.scim.parser.SCIMFilterParser;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.scim.exception.InvalidSyntaxException;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.model.Group;
import io.gravitee.am.gateway.handler.scim.service.GroupService;
import io.gravitee.am.gateway.handler.scim.service.UserService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.json.DecodeException;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class GroupsEndpoint extends AbstractGroupEndpoint {

    private static final int MAX_ITEMS_PER_PAGE = 100;
    private static final int DEFAULT_START_INDEX = 1;

    public GroupsEndpoint(GroupService groupService, ObjectMapper objectMapper, UserService userService, SubjectManager subjectManager) {
        super(groupService, objectMapper, userService, subjectManager);
    }

    public void list(RoutingContext context) {
        // Pagination (https://tools.ietf.org/html/rfc7644#section-3.4.2.4)
        int page = DEFAULT_START_INDEX;
        int size = MAX_ITEMS_PER_PAGE;


        // The 1-based index of the first query result.
        // A value less than 1 SHALL be interpreted as 1.
        try {
            final String startIndex = context.request().getParam("startIndex");
            page = Integer.parseInt(startIndex);
        } catch (Exception ex) {
            log.error("Error parsing 'startIndex' parameter", ex);
        }
        // Non-negative integer. Specifies the desired  results per page, e.g., 10.
        // A negative value SHALL be interpreted as "0".
        // A value of "0"  indicates that no resource results are to be returned except for "totalResults".
        try {
            final String count = context.request().getParam("count");
            size = Integer.min(Integer.parseInt(count), MAX_ITEMS_PER_PAGE);
        } catch (Exception ex) {
            log.error("Error parsing 'count' parameter", ex);
        }

        // Filter results
        Filter filter = null;
        final String filterParam = context.request().getParam("filter");
        if (filterParam != null && !filterParam.isEmpty()) {
            try {
                filter = SCIMFilterParser.parse(filterParam);
            } catch (Exception ex) {
                context.fail(new InvalidSyntaxException(ex.getMessage()));
                return;
            }
        }

        // group service use 0-based index
        groupService.list(filter, page - 1, size, location(context.request()))
                .subscribe(
                        groups -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .end(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(groups)),
                        context::fail);

    }

    /**
     * To create new resources, clients send HTTP POST requests to the resource endpoint, such as "/Groups".
     *
     * The server SHALL process attributes according to the following
     *    mutability rules:
     *
     *      In the request body, attributes whose mutability is "readOnly"
     *       (see Sections 2.2 and 7 of [RFC7643]) SHALL be ignored.
     *
     *    o  Attributes whose mutability is "readWrite" (see Section 2.2 of
     *       [RFC7643]) and that are omitted from the request body MAY be
     *       assumed to be not asserted by the client.  The service provider
     *       MAY assign a default value to non-asserted attributes in the final
     *       resource representation.
     *
     *    o  Service providers MAY take into account whether or not a client
     *       has access to all of the resource's attributes when deciding
     *       whether or not non-asserted attributes should be defaulted.
     *
     *    o  Clients that intend to override existing or server-defaulted
     *       values for attributes MAY specify "null" for a single-valued
     *       attribute or an empty array "[]" for a multi-valued attribute to
     *       clear all values.
     *
     * When the service provider successfully creates the new resource, an
     *    HTTP response SHALL be returned with HTTP status code 201 (Created).
     *    The response body SHOULD contain the service provider's
     *    representation of the newly created resource.  The URI of the created
     *    resource SHALL include, in the HTTP "Location" header and the HTTP
     *    body, a JSON representation [RFC7159] with the attribute
     *    "meta.location".
     *
     * If the service provider determines that the creation of the requested
     *    resource conflicts with existing resources (e.g., a "User" resource
     *    with a duplicate "userName"), the service provider MUST return HTTP
     *    status code 409 (Conflict) with a "scimType" error code of
     *    "uniqueness", as per Section 3.12.
     *
     * See <a href="https://tools.ietf.org/html/rfc7644#section-3.3">3.3. Creating Resources</a>
     */
    public void create(RoutingContext context) {
        try {
            if (context.body() == null) {
                context.fail(new InvalidSyntaxException("Unable to parse body message"));
                return;
            }
            final Group group = context.body().asPojo(Group.class);
            if (group == null) {
                context.fail(new InvalidSyntaxException("Unable to parse body message"));
                return;
            }

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
            final JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
            final String baseUrl = location(context.request());
            principal(accessToken)
                    .map(Optional::ofNullable)
                    .switchIfEmpty(Maybe.just(Optional.empty()))
                    .flatMapSingle(optPrincipal ->  groupService.create(group, baseUrl, optPrincipal.orElse(null)))
                    .subscribe(
                            group1 -> context.response()
                                    .setStatusCode(201)
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
}
