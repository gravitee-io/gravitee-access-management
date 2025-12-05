/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.service.reporter.builder.gateway;

import com.google.common.collect.ImmutableMap;
import io.gravitee.am.authorizationengine.api.model.AuthorizationEngineRequest;
import io.gravitee.am.authorizationengine.api.model.AuthorizationEngineResponse;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.User;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;

import java.util.HashMap;
import java.util.Map;

import static java.util.Optional.ofNullable;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PermissionEvaluatedAuditBuilder extends GatewayAuditBuilder<PermissionEvaluatedAuditBuilder> {
    
    private static final String DECISION_ID_KEY = "decisionId";
    private static final String REQUEST_KEY = "request";
    private static final String RESPONSE_KEY = "response";
    private static final String RESULT_KEY = "result";
    private static final String REASON_KEY = "reason";
    
    private AuthorizationEngineRequest authRequest;
    private AuthorizationEngineResponse authResponse;

    public PermissionEvaluatedAuditBuilder() {
        super();
    }

    /**
     * Sets the authorization engine response containing the decision ID, decision result, and reason.
     * @param response Authorization engine response
     * @return this builder
     */
    public PermissionEvaluatedAuditBuilder response(AuthorizationEngineResponse response) {
        if (response != null) {
            this.authResponse = response;
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
            this.authRequest = request;
            // Set the target based on the resource being accessed
            if (request.resource() != null) {
                setTarget(
                    request.resource().id(),
                    EntityType.AUTHORIZATION_ENGINE,
                    request.resource().type(),
                    request.resource().type() + ":" + request.resource().id(),
                    this.referenceType,
                    this.referenceId
                );
            }
        }
        return this;
    }

    /**
     * Sets the user (subject) who triggered the permission evaluation.
     * @param user User
     * @return this builder
     */
    public PermissionEvaluatedAuditBuilder user(User user) {
        if (user != null) {
            setActor(
                user.getId(),
                EntityType.USER,
                user.getUsername(),
                user.getDisplayName(),
                user.getReferenceType(),
                user.getReferenceId(),
                user.getExternalId(),
                user.getSource()
            );
        }
        return this;
    }

    @Override
    protected AuditEntity createTarget() {
        AuditEntity target = super.createTarget();
        Map<String, Object> attributes = new HashMap<>(ofNullable(target.getAttributes()).orElse(Map.of()));
        
        // Add the decision ID from the response (or fall back to transactional ID if not present)
        String decisionId = authResponse != null && authResponse.decisionId() != null 
            ? authResponse.decisionId() 
            : this.transactionalId;
        attributes.put(DECISION_ID_KEY, decisionId);
        
        // Add the request details
        if (authRequest != null) {
            attributes.put(REQUEST_KEY, buildRequestAttributes(authRequest));
        }
        
        // Add the response details
        if (authResponse != null) {
            attributes.put(RESPONSE_KEY, buildResponseAttributes(authResponse));
        }
        
        target.setAttributes(ImmutableMap.copyOf(attributes));
        return target;
    }

    /**
     * Builds the request attributes map from the authorization engine request.
     * Format:
     * {
     *   "subject": "john",
     *   "action": "hotel.booking.create",
     *   "resource": "room-2025"
     * }
     */
    private Map<String, Object> buildRequestAttributes(AuthorizationEngineRequest request) {
        Map<String, Object> requestMap = new HashMap<>();
        
        if (request.subject() != null) {
            requestMap.put("subject", request.subject().id());
        }
        
        if (request.action() != null) {
            requestMap.put("action", request.action().name());
        }
        
        if (request.resource() != null) {
            requestMap.put("resource", request.resource().id());
        }
        
        return requestMap;
    }

    /**
     * Builds the response attributes map from the authorization engine response.
     * Format:
     * {
     *   "result": true,
     *   "reason": "Tuple matched"
     * }
     */
    private Map<String, Object> buildResponseAttributes(AuthorizationEngineResponse response) {
        Map<String, Object> responseMap = new HashMap<>();
        
        responseMap.put(RESULT_KEY, response.decision());
        
        // Extract reason from context if available
        if (response.context() != null && response.context().containsKey(REASON_KEY)) {
            responseMap.put(REASON_KEY, response.context().get(REASON_KEY));
        } else {
            // Default reason based on decision
            responseMap.put(REASON_KEY, response.decision() ? "Access granted" : "Access denied");
        }
        
        return responseMap;
    }
}
