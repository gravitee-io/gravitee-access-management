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
package io.gravitee.am.authorizationengine.api.audit;

/**
 * Represents a single authorization evaluation event reported by an external engine (e.g., sidecar).
 * Used as a transport DTO between the authorization engine provider and the gateway audit pipeline.
 *
 * @param decisionId    Unique identifier for this evaluation decision
 * @param timestamp     ISO-8601 timestamp of when the evaluation occurred
 * @param decision      Whether access was granted (true) or denied (false)
 * @param principalType Type of the subject evaluated (e.g., "User", "ServiceAccount")
 * @param principalId   Identifier of the subject evaluated
 * @param action        The action that was evaluated (e.g., "read", "delete")
 * @param resourceType  Type of the resource evaluated (e.g., "Document", "Account")
 * @param resourceId    Identifier of the resource evaluated
 * @param engine        Name of the engine that performed the evaluation (e.g., "cedar", "gapl")
 * @author GraviteeSource Team
 */
public record AuthorizationAuditEvent(
        String decisionId,
        String timestamp,
        boolean decision,
        String principalType,
        String principalId,
        String action,
        String resourceType,
        String resourceId,
        String engine
) {}
