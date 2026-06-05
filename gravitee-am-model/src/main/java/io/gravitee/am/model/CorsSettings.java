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
package io.gravitee.am.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */

@Schema(title = "CORS settings", description = "Cross-Origin Resource Sharing configuration controlling which " +
        "web origins may call the domain's endpoints from a browser.")
public class CorsSettings {
    @Schema(description = "Whether CORS handling is enabled for the domain.", defaultValue = "false")
    private boolean enabled;
    @Schema(description = "Origins permitted to make cross-origin requests. Use \"*\" to allow any origin.",
            example = "[\"https://app.example.com\"]")
    private Set<String> allowedOrigins;
    @Schema(description = "HTTP methods permitted on cross-origin requests.",
            example = "[\"GET\",\"POST\",\"PUT\",\"DELETE\"]")
    private Set<String> allowedMethods;
    @Schema(description = "Request headers permitted on cross-origin requests.",
            example = "[\"Authorization\",\"Content-Type\"]")
    private Set<String> allowedHeaders;
    @Schema(description = "How long, in seconds, a browser may cache the result of a preflight request.",
            defaultValue = "86400")
    private int maxAge = 86400;
    @Schema(description = "Whether the browser may send credentials (cookies, authorization headers) with " +
            "cross-origin requests.", defaultValue = "false")
    private boolean allowCredentials;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(Set<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public Set<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(Set<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    public Set<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(Set<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(int maxAge) {
        this.maxAge = maxAge;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    @Override
    public String toString() {
        return "CorsSettings{" +
                "enabled=" + enabled +
                ", allowedOrigins=" + allowedOrigins +
                ", allowedMethods=" + allowedMethods +
                ", allowedHeaders=" + allowedHeaders +
                ", maxAge=" + maxAge +
                ", allowCredentials=" + allowCredentials +
                '}';
    }
}
