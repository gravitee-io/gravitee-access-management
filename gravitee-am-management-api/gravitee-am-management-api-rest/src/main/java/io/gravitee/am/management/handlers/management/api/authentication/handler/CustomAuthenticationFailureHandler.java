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
import io.gravitee.am.management.handlers.management.api.authentication.provider.security.EndUserAuthentication;
import io.gravitee.am.management.handlers.management.api.authentication.provider.security.ManagementAuthenticationContext;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.AuthenticationAuditBuilder;
import io.gravitee.common.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final String CLIENT_ID = "admin";

    @Autowired
    private AuditService auditService;

    public CustomAuthenticationFailureHandler() {
        super();
    }

    public CustomAuthenticationFailureHandler(String defaultFailureUrl) {
        super(defaultFailureUrl);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String organizationId = Organization.DEFAULT;

        EndUserAuthentication authentication = new EndUserAuthentication(request.getParameter("username"), null, new ManagementAuthenticationContext());
        authentication.getContext().set(Claims.ip_address, remoteAddress(request));
        authentication.getContext().set(Claims.user_agent, userAgent(request));
        authentication.getContext().set(Claims.organization, organizationId);

        // audit event
        auditService.report(AuditBuilder.builder(AuthenticationAuditBuilder.class).principal(authentication)
                .referenceType(ReferenceType.ORGANIZATION).referenceId(organizationId)
                .client(CLIENT_ID).throwable(exception));

        super.onAuthenticationFailure(request, response, exception);
    }

    private String remoteAddress(HttpServletRequest httpServerRequest) {
        String xForwardedFor = httpServerRequest.getHeader(HttpHeaders.X_FORWARDED_FOR);
        String remoteAddress;

        if(xForwardedFor != null && xForwardedFor.length() > 0) {
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
