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
package io.gravitee.am.gateway.handler.vertx.handler.scim.endpoint.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.scim.UserService;
import io.gravitee.am.gateway.handler.scim.exception.InvalidSyntaxException;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.model.EntrepriseUser;
import io.gravitee.am.gateway.handler.scim.model.User;
import io.gravitee.am.gateway.handler.vertx.utils.UriBuilderRequest;
import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
public class UpdateUserEndpointHandler implements Handler<RoutingContext>  {

    private static final Logger logger = LoggerFactory.getLogger(UpdateUserEndpointHandler.class);
    private UserService userService;
    private ObjectMapper objectMapper;
    private PasswordValidator passwordValidator;

    public UpdateUserEndpointHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext context) {
        try {
            final User user = Json.decodeValue(context.getBodyAsString(), User.class);
            final String userId = context.request().getParam("id");

            // username is required
            if (user.getUserName() == null || user.getUserName().isEmpty()) {
                context.fail(new InvalidValueException("Field [userName] is required"));
                return;
            }

            // schemas field is REQUIRED and MUST contain valid values and MUST not contain duplicate values
            try {
                checkSchemas(user.getSchemas());
            } catch (Exception ex) {
                context.fail(ex);
                return;
            }

            // password policy
            if (user.getPassword() != null) {
                if (!passwordValidator.validate(user.getPassword())) {
                    context.fail(new InvalidValueException("Field [password] is invalid"));
                    return;
                }
            }

            userService.update(userId, user, location(context.request()))
                    .subscribe(
                            user1 -> context.response()
                                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                    .putHeader(HttpHeaders.LOCATION, user1.getMeta().getLocation())
                                    .end(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(user1)),
                            error -> context.fail(error));
        } catch (DecodeException ex) {
            context.fail(new InvalidSyntaxException("Unable to parse body message", ex));
        }
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setPasswordValidator(PasswordValidator passwordValidator) {
        this.passwordValidator = passwordValidator;
    }

    public static UpdateUserEndpointHandler create(UserService userService) {
        return new UpdateUserEndpointHandler(userService);
    }

    private void checkSchemas(List<String> schemas) throws Exception {
        if (schemas == null || schemas.isEmpty()) {
            throw new InvalidValueException("Field [schemas] is required");
        }
        Set<String> schemaSet = new HashSet();
        // check duplicate and check if values are supported
        schemas.forEach(schema -> {
            if (!schemaSet.add(schema)) {
                throw new InvalidSyntaxException("Duplicate 'schemas' values are forbidden");
            }
            if (!EntrepriseUser.SCHEMAS.contains(schema)) {
                throw new InvalidSyntaxException("The 'schemas' attribute MUST only contain values defined as 'schema' and schemaExtensions' for the resource's defined User type");
            }
        });
    }

    private String location(HttpServerRequest request) {
        try {
            return UriBuilderRequest.resolveProxyRequest(request, request.path(), null);
        } catch (URISyntaxException e) {
            logger.error("An error occurs while decoding SCIM Users location URI", e);
            return "";
        }
    }
}
