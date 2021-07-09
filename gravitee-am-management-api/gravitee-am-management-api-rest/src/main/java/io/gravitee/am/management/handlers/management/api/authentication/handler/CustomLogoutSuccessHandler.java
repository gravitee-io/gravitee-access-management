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

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.management.handlers.management.api.authentication.provider.security.EndUserAuthentication;
import io.gravitee.am.management.handlers.management.api.authentication.web.WebAuthenticationDetails;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.LogoutAuditBuilder;
import org.springframework.core.env.Environment;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomLogoutSuccessHandler extends SimpleUrlLogoutSuccessHandler {

    private static final String LOGOUT_URL_PARAMETER = "target_url";

    private final AuditService auditService;

    private final Environment environment;

    private final JWTParser jwtParser;

    private final OrganizationUserService userService;

    private final String authCookieName;

    public CustomLogoutSuccessHandler(AuditService auditService, Environment environment, JWTParser jwtParser, OrganizationUserService userService) {
        this.auditService = auditService;
        this.environment = environment;
        this.jwtParser = jwtParser;
        this.userService = userService;
        this.authCookieName = environment.getProperty("jwt.cookie-name", "Auth-Graviteeio-AM");
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
        String logoutRedirectUrl = request.getParameter(LOGOUT_URL_PARAMETER);
        if (logoutRedirectUrl != null && !logoutRedirectUrl.isEmpty()) {
            setTargetUrlParameter(LOGOUT_URL_PARAMETER);
        }

        final Cookie[] cookies = request.getCookies();
        final Optional<Cookie> authCookie = Stream.of(cookies).filter(c -> authCookieName.equals(c.getName())).findFirst();
        authCookie.ifPresent(cookie -> {
            try {
                final String jwtStr = cookie.getValue().substring("Bearer ".length());
                final JWT jwt = jwtParser.parse(jwtStr);
                WebAuthenticationDetails details = new WebAuthenticationDetails(request);

                // read user profile to obtain same information as login step.
                // if the read fails, trace only with information available into the cookie
                userService.findById(ReferenceType.ORGANIZATION, (String) jwt.get("org"), (String) jwt.getSub())
                        .doOnSuccess(user -> auditService.report(AuditBuilder.builder(LogoutAuditBuilder.class).user(user)
                                .referenceType(ReferenceType.ORGANIZATION).referenceId((String) jwt.get("org"))
                                .ipAddress(details.getRemoteAddress())
                                .userAgent(details.getUserAgent())))
                        .doOnError(err -> {
                            logger.warn("Unable to read user information, trace logout with minimal data", err);
                            auditService.report(AuditBuilder.builder(LogoutAuditBuilder.class)
                                    .principal(new EndUserAuthentication(jwt.get("username"), null, new SimpleAuthenticationContext()))
                                    .referenceType(ReferenceType.ORGANIZATION).referenceId((String) jwt.get("org"))
                                    .ipAddress(details.getRemoteAddress())
                                    .userAgent(details.getUserAgent()));
                        })
                        .subscribe();
            } catch (Exception e) {
                logger.warn("Unable to extract information from authentication cookie", e);
            }
        });

        return super.determineTargetUrl(request, response);
    }
}
