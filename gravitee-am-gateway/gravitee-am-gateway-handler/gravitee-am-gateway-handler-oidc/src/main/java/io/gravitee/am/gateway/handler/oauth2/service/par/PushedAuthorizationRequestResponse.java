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
package io.gravitee.am.gateway.handler.oauth2.service.par;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * See https://datatracker.ietf.org/doc/html/draft-ietf-oauth-par-10#section-2.2
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PushedAuthorizationRequestResponse {
    // The request URI corresponding to the authorization request posted.
    @JsonProperty("request_uri")
    private String requestUri;

    // A JSON number that represents the lifetime of the request URI in seconds as a positive integer
    @JsonProperty("expires_in")
    private long exp;

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    public long getExp() {
        return exp;
    }

    public void setExp(long exp) {
        this.exp = exp;
    }
}
