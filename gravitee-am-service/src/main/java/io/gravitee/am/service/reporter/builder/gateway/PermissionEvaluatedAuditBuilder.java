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
package io.gravitee.am.service.reporter.builder.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.gravitee.am.authorizationengine.api.model.AuthorizationEngineRequest;
import io.gravitee.am.authorizationengine.api.model.AuthorizationEngineResponse;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PermissionEvaluatedAuditBuilder extends GatewayAuditBuilder<PermissionEvaluatedAuditBuilder> {
    
    private static final String DECISION_ID_KEY = "decisionId";
    private static final String REQUEST_KEY = "request";
    private static final String RESPONSE_KEY = "response";
    private static final String RESULT_KEY = "result";

    private final Map<String, Object> data = new HashMap<>();

    public PermissionEvaluatedAuditBuilder() {
        super();
        type(EventType.PERMISSION_EVALUATED);
    }

    /**
     * Sets the authorization engine response containing the decision ID, decision result, and reason.
     * @param response Authorization engine response
     * @return this builder
     */
    public PermissionEvaluatedAuditBuilder response(AuthorizationEngineResponse response) {
        if (response != null) {
            this.data.put(RESPONSE_KEY, response);
            this.data.put(RESULT_KEY, response.decision());
        }
        return this;
    }

    /**
     * Sets the decision ID.
     * @param decisionId Decision ID
     * @return this builder
     */
    public PermissionEvaluatedAuditBuilder decisionId(String decisionId) {
        if (!Strings.isNullOrEmpty(decisionId)) {
            this.data.put(DECISION_ID_KEY, decisionId);
        }
        return this;
    }

    /**
     * Sets the domain reference for this audit event.
     * @param domain Domain
     * @return this builder
     */
    public PermissionEvaluatedAuditBuilder domain(Domain domain) {
        if (domain != null) {
            reference(Reference.domain(domain.getId()));
        }
        return this;
    }

    /**
     * Sets the authorization engine request containing subject, action, and resource details.
     * @param request Authorization engine request
     * @return this builder
     */
    public PermissionEvaluatedAuditBuilder request(AuthorizationEngineRequest request) {
        if (request != null) {
            this.data.put(REQUEST_KEY, request);
        }
        return this;
    }

    /**
     * Sets the client/application that is making the authorization check.
     * This becomes the actor in the audit event.
     * Note: The subject (user) being checked is captured in the request attributes.
     * 
     * @param client Client making the authorization check
     * @return this builder
     */
    public PermissionEvaluatedAuditBuilder actor(Client client) {
        if (client != null) {
            // Set as both access point and actor
            client(client);
            setActor(
                client.getId(),
                EntityType.APPLICATION,
                client.getClientName(),
                client.getClientName(),
                this.referenceType,
                this.referenceId
            );
        }
        return this;
    }

    /**
     * Override build so we can set the newValue to be the data map of the request/response
     * @param mapper ObjectMapper
     * @return Audit object
     */
    @Override
    public Audit build(ObjectMapper mapper) {
        if (!data.isEmpty()) {
            this.setNewValue(data);
        }
        return super.build(mapper);
    }
}
