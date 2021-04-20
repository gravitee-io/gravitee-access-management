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
package io.gravitee.am.gateway.handler.oauth2.service.response;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.common.web.UriBuilder;
import java.net.URISyntaxException;

/**
 * When using the Implicit Flow, all response parameters are added to the fragment component of the Redirection URI,
 * as specified in OAuth 2.0 Multiple Response Type Encoding Practices [OAuth.Responses], unless a different Response Mode was specified.
 *
 * ID token is returned if the response_type value used is id_token.
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#HybridAuthResponse">3.3.2.5. Successful Authentication Response</a>
 * See <a href="https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html#id_token">3. ID Token Response Type</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IDTokenResponse extends AuthorizationResponse {

    private String idToken;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    @Override
    public String buildRedirectUri() throws URISyntaxException {
        UriBuilder uriBuilder = UriBuilder.fromURIString(getRedirectUri());
        uriBuilder.addFragmentParameter(ResponseType.ID_TOKEN, getIdToken());
        if (getState() != null) {
            uriBuilder.addFragmentParameter(Parameters.STATE, getState());
        }
        return uriBuilder.build().toString();
    }
}
