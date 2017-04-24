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
package io.gravitee.am.gateway.handler.oauth2.provider.security.web.authentication;

import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.oauth2.common.util.OAuth2Utils;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientAwareAuthenticationDetailsSource implements AuthenticationDetailsSource<HttpServletRequest, Map<String, String>> {

    public ClientAwareAuthenticationDetailsSource() {
    }

    public Map<String, String> buildDetails(HttpServletRequest context) {
        WebAuthenticationDetails details = new WebAuthenticationDetails(context);
        Map<String, String> mapDetails = new HashMap<>();
        mapDetails.put("remote_address", details.getRemoteAddress());
        mapDetails.put("session_id", details.getSessionId());
        mapDetails.put(OAuth2Utils.CLIENT_ID, context.getParameter(OAuth2Utils.CLIENT_ID));

        return mapDetails;
    }
}
