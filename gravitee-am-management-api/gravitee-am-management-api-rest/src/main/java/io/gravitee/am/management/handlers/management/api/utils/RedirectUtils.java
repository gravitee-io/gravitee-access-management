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
package io.gravitee.am.management.handlers.management.api.utils;

import io.gravitee.common.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RedirectUtils {
    public static UriComponentsBuilder preBuildLocationHeader(HttpServletRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();

        String scheme = request.getHeader(HttpHeaders.X_FORWARDED_PROTO);
        if (scheme != null && !scheme.isEmpty()) {
            builder.scheme(scheme);
        } else {
            builder.scheme(request.getScheme());
        }

        String host = request.getHeader(HttpHeaders.X_FORWARDED_HOST);
        if (host != null && !host.isEmpty()) {
            if (host.contains(":")) {
                // Forwarded host contains both host and port
                String[] parts = host.split(":");
                builder.host(parts[0]);
                builder.port(parts[1]);
            } else {
                builder.host(host);
            }
        } else {
            builder.host(request.getServerName());
            if (request.getServerPort() != 80 && request.getServerPort() != 443) {
                builder.port(request.getServerPort());
            }
        }
        return builder;
    }

    /**
     * Builds a redirect URL by combining a base URI and a redirect path.
     * Handles trailing slashes properly to prevent double slashes which are rejected by Jetty.
     * 
     * @param redirectUri the base URI (may have trailing slashes)
     * @param redirectPath the path to append (may or may not start with slash)
     * @return the properly formatted redirect URL
     */
    public static String buildCockpitRedirectUrl(String redirectUri, String redirectPath) {
        if (redirectUri == null) {
            return redirectPath;
        }
        
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri);
            
            // Remove trailing slashes from the base path
            String basePath = builder.build().getPath();
            if (basePath != null && basePath.endsWith("/")) {
                builder.replacePath(basePath.replaceAll("/+$", ""));
            }
            
            // Add the redirect path
            if (redirectPath != null && !redirectPath.isEmpty()) {
                if (!redirectPath.startsWith("/")) {
                    redirectPath = "/" + redirectPath;
                }
                builder.path(redirectPath);
            }
            
            return builder.build().toUriString();
        } catch (Exception e) {
            // Fallback to simple concatenation if UriComponentsBuilder fails
            return redirectUri.replaceAll("/+$", "") + 
                   (redirectPath != null && !redirectPath.isEmpty() && !redirectPath.startsWith("/") ? "/" : "") + 
                   redirectPath;
        }
    }
}
