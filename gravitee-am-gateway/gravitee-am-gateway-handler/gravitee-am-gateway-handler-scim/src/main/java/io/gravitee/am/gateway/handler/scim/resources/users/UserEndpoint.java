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
package io.gravitee.am.gateway.handler.scim.resources.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.scim.business.PatchUserAction;
import io.gravitee.am.gateway.handler.scim.business.UpdateUserAction;
import io.gravitee.am.gateway.handler.scim.exception.InvalidSyntaxException;
import io.gravitee.am.gateway.handler.scim.service.ProvisioningUserService;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Map;
import java.util.Optional;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserEndpoint extends AbstractUserEndpoint {

    public UserEndpoint(Domain domain, ProvisioningUserService userService, ObjectMapper objectMapper, SubjectManager subjectManager) {
        super(domain, userService, objectMapper, subjectManager);
    }

    public void get(RoutingContext context) {
        final String userId = context.request().getParam("id");
        userService
                .get(userId, location(context.request()))
                .subscribe(
                        user -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .putHeader(HttpHeaders.LOCATION, user.getMeta().getLocation())
                                .end(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(user)),
                        context::fail,
                        () -> context.fail(new UserNotFoundException(userId)));
    }

    /**
     * As the operation's intent is to replace all attributes, SCIM clients
     * MAY send all attributes, regardless of each attribute's mutability.
     * The server will apply attribute-by-attribute replacements according
     * to the following attribute mutability rules:
     * <p>
     * readWrite, writeOnly  Any values provided SHALL replace the existing
     * attribute values.
     * <p>
     * Attributes whose mutability is "readWrite" that are omitted from
     * the request body MAY be assumed to be not asserted by the client.
     * The service provider MAY assume that any existing values are to be
     * cleared, or the service provider MAY assign a default value to the
     * final resource representation.  Service providers MAY take into
     * account whether or not a client has access to, or understands, all
     * of the resource's attributes when deciding whether non-asserted
     * attributes SHALL be removed or defaulted.  Clients that want to
     * override a server's defaults MAY specify "null" for a
     * single-valued attribute, or an empty array "[]" for a multi-valued
     * attribute, to clear all values.
     * <p>
     * immutable  If one or more values are already set for the attribute,
     * the input value(s) MUST match, or HTTP status code 400 SHOULD be
     * returned with a "scimType" error code of "mutability".  If the
     * service provider has no existing values, the new value(s) SHALL be
     * applied.
     * <p>
     * readOnly  Any values provided SHALL be ignored.
     * <p>
     * If an attribute is "required", clients MUST specify the attribute in
     * the PUT request.
     * <p>
     *
     * See <a href="https://tools.ietf.org/html/rfc7644#section-3.5.1">3.5.1. Replacing with PUT</a>
     */
    public void update(RoutingContext context) {
        try {

            final String body = context.body().asString();
            if (body == null) {
                context.fail(new InvalidSyntaxException("Unable to parse body message"));
                return;
            }

            final Map<String, Object> payload = Json.decodeValue(body, Map.class);

            final SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(new VertxHttpServerRequest(context.request().getDelegate()));
            authenticationContext.attributes().putAll(context.data());

            final String userId = context.request().getParam("id");

            final JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
            final String baseUrl = location(context.request());
            principal(accessToken)
                    .map(Optional::ofNullable)
                    .switchIfEmpty(Maybe.just(Optional.empty()))
                    .flatMapSingle(optPrincipal -> new UpdateUserAction(userService, getDomain(), context.get(CLIENT_CONTEXT_KEY))
                            .execute(userId, baseUrl, payload, authenticationContext, optPrincipal.orElse(null)))
                    .subscribe(
                            user1 -> context.response()
                                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                    .putHeader(HttpHeaders.LOCATION, user1.getMeta().getLocation())
                                    .end(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(user1)),
                            context::fail);
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
            final String body = context.body().asString();
            if (body == null) {
                context.fail(new InvalidSyntaxException("Unable to parse body message"));
                return;
            }
            final Map<String, Object> payload = Json.decodeValue(body, Map.class);

            final String userId = context.request().getParam("id");

            final SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(new VertxHttpServerRequest(context.request().getDelegate()));
            authenticationContext.attributes().putAll(context.data());

            final JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);

            final String baseUrl = location(context.request());

            principal(accessToken)
                    .map(Optional::ofNullable)
                    .switchIfEmpty(Maybe.just(Optional.empty()))
                    .flatMapSingle(optPrincipal -> new PatchUserAction(userService, getDomain(), context.get(CLIENT_CONTEXT_KEY))
                            .execute(userId, baseUrl, payload, authenticationContext, optPrincipal.orElse(null)))
                    .subscribe(
                            user1 -> context.response()
                                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                    .putHeader(HttpHeaders.LOCATION, user1.getMeta().getLocation())
                                    .end(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(user1)),
                            context::fail);
        } catch (DecodeException ex) {
            context.fail(new InvalidSyntaxException("Unable to parse body message", ex));
        }
    }

    /**
     * Clients request resource removal via DELETE.  Service providers MAY
     * choose not to permanently delete the resource but MUST return a 404
     * (Not Found) error code for all operations associated with the
     * previously deleted resource.  Service providers MUST omit the
     * resource from future query results.  In addition, the service
     * provider SHOULD NOT consider the deleted resource in conflict
     * calculation.  For example, if a User resource is deleted, a CREATE
     * request for a User resource with the same userName as the previously
     * deleted resource SHOULD NOT fail with a 409 error due to userName
     * conflict.
     * <p>
     * In response to a successful DELETE, the server SHALL return a
     * successful HTTP status code 204 (No Content).
     * <p>
     * See <a href="https://tools.ietf.org/html/rfc7644#section-3.6>3.6. Deleting Resources</a>
     */
    public void delete(RoutingContext context) {
        final String userId = context.request().getParam("id");
        final JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);

        principal(accessToken)
                .map(Optional::ofNullable)
                .switchIfEmpty(Maybe.just(Optional.empty()))
                .flatMapCompletable(optPrincipal -> userService.delete(userId, optPrincipal.orElse(null)))
                .subscribe(
                        () -> context.response().setStatusCode(204).end(),
                        context::fail);
    }
}
