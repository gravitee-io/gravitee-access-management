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
package io.gravitee.am.gateway.handler.oauth2.request;

import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;

/**
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.1.3"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenRequest extends BaseRequest {

    private String grantType;
    private String username;
    private String password;
    private String subject;

    public String getGrantType() {
        return grantType;
    }

    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public OAuth2Request createOAuth2Request() {
        MultiValueMap<String, String> requestParameters = getRequestParameters();
        MultiValueMap<String, String> safeRequestParameters = new LinkedMultiValueMap(requestParameters);

        // Remove password if present to prevent leaks
        safeRequestParameters.remove("password");
        safeRequestParameters.remove("client_secret");

        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setOrigin(getOrigin());
        oAuth2Request.setClientId(getClientId());
        oAuth2Request.setScopes(getScopes());
        oAuth2Request.setRequestParameters(safeRequestParameters);
        oAuth2Request.setGrantType(getGrantType());
        oAuth2Request.setSubject(getSubject());
        oAuth2Request.setAdditionalParameters(getAdditionalParameters());

        return oAuth2Request;
    }
}

