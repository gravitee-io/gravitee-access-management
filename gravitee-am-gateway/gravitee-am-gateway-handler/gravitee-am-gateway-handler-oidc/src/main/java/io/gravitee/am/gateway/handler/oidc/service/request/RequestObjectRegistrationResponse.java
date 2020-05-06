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
package io.gravitee.am.gateway.handler.oidc.service.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Teams
 */
public class RequestObjectRegistrationResponse {

    // A JSON string that represents the issuer identifier of the authorization server as defined in [RFC7519]. When a
    // pure OAuth 2.0 is used, the value is the redirection URI. When OpenID Connect is used, the value is the issuer
    // value of the authorization server.
    private String iss;

    // A JSON string that represents the client identifier of the client that posted the request object.
    private String aud;

    // The request URI corresponding to the request object posted.
    @JsonProperty("request_uri")
    private String requestUri;

    // A JSON number that represents the expiry time of the request URI as defined in [RFC7519].
    private long exp;

    public String getIss() {
        return iss;
    }

    public void setIss(String iss) {
        this.iss = iss;
    }

    public String getAud() {
        return aud;
    }

    public void setAud(String aud) {
        this.aud = aud;
    }

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
