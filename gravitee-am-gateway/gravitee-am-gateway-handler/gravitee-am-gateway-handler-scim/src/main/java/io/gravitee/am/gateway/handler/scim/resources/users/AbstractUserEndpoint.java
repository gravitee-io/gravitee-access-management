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
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.scim.exception.InvalidSyntaxException;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.model.User;
import io.gravitee.am.gateway.handler.scim.service.UserService;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Domain;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

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
    private Domain domain;

    public AbstractUserEndpoint(Domain domain, UserService userService, ObjectMapper objectMapper) {
        this.domain = domain;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    protected void checkSchemas(List<String> schemas, List<String> restrictedSchemas) {
        if (schemas == null || schemas.isEmpty()) {
            throw new InvalidValueException("Field [schemas] is required");
        }
        Set<String> schemaSet = new HashSet<>();
        // check duplicate and check if values are supported
        schemas.forEach(schema -> {
            if (!schemaSet.add(schema)) {
                throw new InvalidSyntaxException("Duplicate 'schemas' values are forbidden");
            }
            if (!restrictedSchemas.contains(schema)) {
                throw new InvalidSyntaxException("The 'schemas' attribute MUST only contain values defined as 'schema' and 'schemaExtensions' for the resource's defined type");
            }
        });
    }

    protected String location(HttpServerRequest request) {
        return UriBuilderRequest.resolveProxyRequest(request, request.path());
    }

    protected String userSource(RoutingContext context) {
        if (domain.getScim() != null
                && domain.getScim().isIdpSelectionEnabled()
                && !StringUtils.isEmpty(domain.getScim().getIdpSelectionRule())) {
            try {
                SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(new VertxHttpServerRequest(context.request().getDelegate()));
                authenticationContext.attributes().putAll(context.data());
                return authenticationContext.getTemplateEngine().getValue(domain.getScim().getIdpSelectionRule(), String.class);
            } catch (Exception ex) {
                logger.error("An error has occurred when evaluating IdP selection rule", ex);
            }
        }
        return null;
    }
}
