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

package io.gravitee.am.gateway.handler.scim.business;


import io.gravitee.am.gateway.handler.scim.exception.InvalidSyntaxException;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.model.EnterpriseUser;
import io.gravitee.am.gateway.handler.scim.model.GraviteeUser;
import io.gravitee.am.gateway.handler.scim.model.User;
import io.gravitee.am.gateway.handler.scim.service.ProvisioningUserService;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public abstract class AbstractUserAction {
    protected ProvisioningUserService userService;
    protected Domain domain;
    protected Client client;

    protected AbstractUserAction(ProvisioningUserService userService, Domain domain, Client client) {
        this.userService = userService;
        this.domain = domain;
        this.client = client;
    }

    protected final User extractUser(Map<String, Object> payload) {
        final List<String> schemas = (List<String>) Optional.ofNullable(payload.get("schemas")).orElse(Collections.emptyList());
        try {
            final User user = evaluateUser(schemas, payload);
            return user;
        } catch (IllegalArgumentException e) {
            log.debug("IllegalArgumentException received during scim user deserialization", e);
            throw new InvalidValueException(e.getMessage());
        }
    }

    private User evaluateUser(List<String> schemas, Map<String, Object> payload) {
        if (EnterpriseUser.SCHEMAS.containsAll(schemas)) {
            return new JsonObject(payload).mapTo(EnterpriseUser.class);
        }
        if (GraviteeUser.SCHEMAS.containsAll(schemas) || GraviteeUser.SCHEMAS_WITH_ENTERPRISE.containsAll(schemas)) {
            return new JsonObject(payload).mapTo(GraviteeUser.class);
        }
        return new JsonObject(payload).mapTo(User.class);
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

    protected Maybe<Optional<String>> userSource(AuthenticationContext authenticationContext) {
        if (domain.getScim() != null
                && domain.getScim().isIdpSelectionEnabled()
                && StringUtils.hasText(domain.getScim().getIdpSelectionRule())) {
            try {
                return authenticationContext.getTemplateEngine()
                        .eval(domain.getScim().getIdpSelectionRule(), String.class)
                        .map(Optional::ofNullable)
                        .switchIfEmpty(Maybe.just(Optional.empty()));
            } catch (Exception ex) {
                log.error("An error has occurred when evaluating IdP selection rule", ex);
            }
        }
        return Maybe.just(Optional.empty());
    }
}
