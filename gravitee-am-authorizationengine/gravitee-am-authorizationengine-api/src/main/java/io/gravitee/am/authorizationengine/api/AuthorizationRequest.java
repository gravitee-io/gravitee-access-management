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
package io.gravitee.am.authorizationengine.api;

import lombok.Builder;

import java.util.Map;

/**
 * Authorization context following the AuthZen request format.
 *
 * @param subject  The subject of the authorization check
 * @param resource The resource being accessed
 * @param action   The action being performed
 * @param context  Additional context information
 * @see <a href="https://openid.github.io/authzen/">AuthZen Specification</a>
 * @author GraviteeSource Team
 */
@Builder
public record AuthorizationRequest(
        Subject subject,
        Resource resource,
        Action action,
        Context context
) {

    /**
     * Represents the subject (user/entity) making the request.
     *
     * @param type       Type of the subject (e.g., "user", "service")
     * @param id         Unique identifier for the subject
     * @param properties Additional subject properties
     */
    @Builder
    public record Subject(
            String type,
            String id,
            Map<String, Object> properties
    ) {}

    /**
     * Represents the resource being accessed.
     *
     * @param type       Type of the resource (e.g., "account", "document")
     * @param id         Unique identifier for the resource
     * @param properties Additional resource properties
     */
    @Builder
    public record Resource(
            String type,
            String id,
            Map<String, Object> properties
    ) {}

    /**
     * Represents the action being performed on the resource.
     *
     * @param name       Name of the action (e.g., "can_read", "can_write")
     * @param properties Additional action properties (e.g., HTTP method)
     */
    @Builder
    public record Action(
            String name,
            Map<String, Object> properties
    ) {}

    /**
     * Represents additional contextual information for the authorization decision.
     * This is a flexible object containing environmental attributes (e.g., time, IP address, etc.).
     *
     * @param attributes Context attributes as key-value pairs.
     *                   Common attributes include:
     *                   - time: Timestamp of the request (e.g., "1985-10-26T01:22-07:00")
     *                   - ip: IP address of the requester
     */
    @Builder
    public record Context(
            Map<String, Object> attributes
    ) {}
}
