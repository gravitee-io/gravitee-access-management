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
package io.gravitee.am.management.handlers.management.api.authentication.web;

import io.gravitee.am.model.Organization;
import io.gravitee.common.http.HttpHeaders;

import jakarta.servlet.http.HttpServletRequest;
import java.io.Serializable;

import static io.gravitee.am.management.handlers.management.api.authentication.controller.LoginController.ORGANIZATION_PARAMETER_NAME;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthenticationDetails implements Serializable {

    private final String remoteAddress;
    private final String userAgent;
    private String organizationId;

    /**
     * Records the remote address, user-agent and organization.
     *
     * @param request that the authentication request was received from
     */
    public WebAuthenticationDetails(HttpServletRequest request) {
        this.remoteAddress = remoteAddress(request);
        this.userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        this.organizationId = request.getParameter(ORGANIZATION_PARAMETER_NAME);

        if (this.organizationId == null) {
            this.organizationId = Organization.DEFAULT;
        }
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString()).append(": ");
        sb.append("RemoteIpAddress: ").append(this.getRemoteAddress()).append("; ");
        sb.append("UserAgent: ").append(this.getUserAgent());

        return sb.toString();
    }

    private String remoteAddress(HttpServletRequest httpServerRequest) {
        String xForwardedFor = httpServerRequest.getHeader(HttpHeaders.X_FORWARDED_FOR);
        String remoteAddress;

        if (xForwardedFor != null && xForwardedFor.length() > 0) {
            int idx = xForwardedFor.indexOf(',');

            remoteAddress = (idx != -1) ? xForwardedFor.substring(0, idx) : xForwardedFor;

            idx = remoteAddress.indexOf(':');

            remoteAddress = (idx != -1) ? remoteAddress.substring(0, idx).trim() : remoteAddress.trim();
        } else {
            remoteAddress = httpServerRequest.getRemoteAddr();
        }

        return remoteAddress;
    }
}
