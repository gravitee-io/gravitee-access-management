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

import io.gravitee.am.management.handlers.management.api.authentication.provider.generator.JWTGenerator;
import io.gravitee.am.management.handlers.management.api.authentication.provider.generator.RedirectCookieGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CookieClearingLogoutHandler implements LogoutHandler {

    @Autowired
    private JWTGenerator jwtGenerator;

    @Autowired
    private RedirectCookieGenerator redirectCookieGenerator;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {

        response.addCookie(jwtGenerator.getClearCookie());
        response.addCookie(redirectCookieGenerator.getClearCookie());
    }
}
