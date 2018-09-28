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
package io.gravitee.am.gateway.handler.oauth2.response;

import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;

import java.io.Serializable;
import java.net.URISyntaxException;

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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public abstract String buildRedirectUri(AuthorizationRequest authorizationRequest) throws URISyntaxException;
}
