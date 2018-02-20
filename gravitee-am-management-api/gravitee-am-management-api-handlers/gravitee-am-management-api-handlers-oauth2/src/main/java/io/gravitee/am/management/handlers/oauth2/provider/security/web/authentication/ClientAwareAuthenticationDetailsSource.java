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
package io.gravitee.am.management.handlers.oauth2.provider.security.web.authentication;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.security.oauth2.common.util.OAuth2Utils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientAwareAuthenticationDetailsSource implements AuthenticationDetailsSource<HttpServletRequest, Map<String, String>> {

    public ClientAwareAuthenticationDetailsSource() {
    }

    public Map<String, String> buildDetails(HttpServletRequest request) {
        Map<String, String> mapDetails = new HashMap<>();
        mapDetails.put("remote_address", request.getRemoteAddr());

        HttpSession session = request.getSession(false);
        mapDetails.put("session_id",  (session != null) ? session.getId() : null);

        String clientId = request.getParameter(OAuth2Utils.CLIENT_ID);

        // In case of basic authentication, extract client_id from authorization header
        if (clientId == null || clientId.isEmpty()) {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Basic ")) {
                try {
                    String[] tokens = extractAndDecodeHeader(header);
                    clientId = tokens[0];
                } catch (IOException ioe) {
                    // Nothing to do
                }
            }
        }

        mapDetails.put(OAuth2Utils.CLIENT_ID, clientId);

        return mapDetails;
    }

    private String[] extractAndDecodeHeader(String header) throws IOException {

        byte[] base64Token = header.substring(6).getBytes("UTF-8");
        byte[] decoded;
        try {
            decoded = Base64.decode(base64Token);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Failed to decode basic authentication token");
        }

        String token = new String(decoded, "UTF-8");

        int delim = token.indexOf(":");

        if (delim == -1) {
            throw new BadCredentialsException("Invalid basic authentication token");
        }
        return new String[] {token.substring(0, delim), token.substring(delim + 1)};
    }
}
