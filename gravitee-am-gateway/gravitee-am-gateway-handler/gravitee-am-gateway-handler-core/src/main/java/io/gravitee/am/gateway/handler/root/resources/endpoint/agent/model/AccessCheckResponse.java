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
package io.gravitee.am.gateway.handler.root.resources.endpoint.agent.model;

/**
 * @author GraviteeSource Team
 */
public class AccessCheckResponse {

    private boolean allowed;
    private String reason; // "ok" | "mcp_denied" | "tuple_missing" | "upstream_error"

    public AccessCheckResponse() {
    }

    public AccessCheckResponse(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public static AccessCheckResponse ok() {
        return new AccessCheckResponse(true, "ok");
    }

    public static AccessCheckResponse deny(String reason) {
        return new AccessCheckResponse(false, reason);
    }
}