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
package io.gravitee.am.management.handlers.management.api.authentication.handler;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.management.handlers.management.api.authentication.provider.security.EndUserAuthentication;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.AuthenticationAuditBuilder;
import io.gravitee.common.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static io.gravitee.am.management.handlers.management.api.authentication.controller.LoginController.ORGANIZATION_PARAMETER_NAME;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Autowired
    private AuditService auditService;

    private String defaultFailureUrl;

    public CustomAuthenticationFailureHandler(String defaultFailureUrl) {
        super();
        this.defaultFailureUrl = defaultFailureUrl;
        super.setAllowSessionCreation(false);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String organizationId = getOrganizationId(request);

        EndUserAuthentication authentication = new EndUserAuthentication(request.getParameter("username"), null, new SimpleAuthenticationContext());
        authentication.getContext().set(Claims.ip_address, remoteAddress(request));
        authentication.getContext().set(Claims.user_agent, userAgent(request));
        authentication.getContext().set(Claims.organization, organizationId);

        // audit event
        auditService.report(AuditBuilder.builder(AuthenticationAuditBuilder.class).principal(authentication)
                .referenceType(ReferenceType.ORGANIZATION).referenceId(organizationId).throwable(exception));

        String redirectUri = defaultFailureUrl;

        if (!Organization.DEFAULT.equals(organizationId)) {
            redirectUri = UriBuilder.fromURIString(defaultFailureUrl).addParameter("organizationId", organizationId).buildString();
        }

        super.getRedirectStrategy().sendRedirect(request, response, redirectUri);
    }

    private String getOrganizationId(HttpServletRequest request) {

        String organizationId = request.getParameter(ORGANIZATION_PARAMETER_NAME);

        if (organizationId == null) {
            organizationId = Organization.DEFAULT;
        }

        return organizationId;
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

    private String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}
