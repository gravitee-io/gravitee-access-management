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
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.scim.exception.InvalidSyntaxException;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.model.EntrepriseUser;
import io.gravitee.am.gateway.handler.scim.service.UserService;
import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.vertx.reactivex.core.http.HttpServerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AbstractUserEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(AbstractUserEndpoint.class);
    protected UserService userService;
    protected ObjectMapper objectMapper;
    protected PasswordValidator passwordValidator;

    public AbstractUserEndpoint(UserService userService, ObjectMapper objectMapper, PasswordValidator passwordValidator) {
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.passwordValidator = passwordValidator;
    }

    protected void checkSchemas(List<String> schemas) throws Exception {
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

    protected String location(HttpServerRequest request) {
        return UriBuilderRequest.resolveProxyRequest(request, request.path());
    }
}
