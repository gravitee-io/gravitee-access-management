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
package io.gravitee.am.management.handlers.management.api.authentication.controller;

import io.gravitee.am.management.handlers.management.api.authentication.provider.generator.RedirectCookieGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Controller
public class AuthorizationController {

    private static final String REDIRECT_URI = "redirect_uri";

    @Autowired
    private RedirectCookieGenerator redirectCookieGenerator;

    @RequestMapping(value = "/authorize")
    public void authorize(HttpServletResponse response, @RequestParam Map<String, String> parameters) throws IOException {

        String redirectUriParameter = parameters.get(REDIRECT_URI);
        if (redirectUriParameter == null || redirectUriParameter.isEmpty()) {
            throw new IllegalArgumentException("A redirectUri must be either supplied");
        }

        // Redirect cookie is no longer useful.
        response.addCookie(redirectCookieGenerator.getClearCookie());

        response.sendRedirect(redirectUriParameter);
    }
}
