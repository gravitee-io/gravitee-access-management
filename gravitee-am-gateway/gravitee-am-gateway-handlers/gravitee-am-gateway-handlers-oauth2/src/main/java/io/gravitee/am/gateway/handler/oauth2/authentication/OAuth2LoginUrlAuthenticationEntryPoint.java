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
package io.gravitee.am.gateway.handler.oauth2.authentication;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2LoginUrlAuthenticationEntryPoint extends LoginUrlAuthenticationEntryPoint {

    public OAuth2LoginUrlAuthenticationEntryPoint(String loginFormUrl) {
        super(loginFormUrl);
    }

    @Override
    protected String determineUrlToUseForThisRequest(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        String url = super.determineUrlToUseForThisRequest(request, response, exception);
        // Does not work because ant pattern matcher does not allow to pass query parameters
    //    return UriComponentsBuilder.fromPath(url).queryParam("client_id", request.getParameter("client_id")).toUriString();
        return url;
    }
}
