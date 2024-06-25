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

import io.gravitee.am.common.web.UriBuilder;
import io.vertx.rxjava3.core.MultiMap;

import java.io.Serializable;

/**
 * Response after authorization code flow or implicit flow or hybrid flow
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AuthorizationResponse implements Serializable {

    /**
     *  REQUIRED if the "state" parameter was present in the client authorization request.
     *  The exact value received from the client.
     */
    private String state;

    /**
     * Response redirect_uri
     */
    private String redirectUri;

    private String responseMode;

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getResponseMode() {
        return responseMode;
    }

    public void setResponseMode(String responseMode) {
        this.responseMode = responseMode;
    }

    public abstract String buildRedirectUri();

    /**
     * @return list of parameters to send back to the callback URL.
     * @param encodeState false value should be used only when response_mode is set to form_post.
     * @return
     */
    public abstract MultiMap params(boolean encodeState);

    /**
     * @return list of parameters to send back to the callback URL. The state need to be URL encoded.
     */
    public final MultiMap params() {
        return this.params(true);
    }

    protected String getURLEncodedState() {
        return UriBuilder.encodeURIComponent(getState());
    }
}
