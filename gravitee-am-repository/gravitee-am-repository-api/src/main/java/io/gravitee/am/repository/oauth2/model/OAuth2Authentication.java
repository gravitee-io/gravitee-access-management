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
package io.gravitee.am.repository.oauth2.model;

import io.gravitee.am.repository.oauth2.model.authentication.AbstractAuthenticationToken;
import io.gravitee.am.repository.oauth2.model.authentication.Authentication;
import io.gravitee.am.repository.oauth2.model.request.OAuth2Request;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2Authentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = -8047157869322892572L;

    private final OAuth2Request storedRequest;

    private final Authentication userAuthentication;

    public OAuth2Authentication(OAuth2Request storedRequest, Authentication userAuthentication) {
        super(userAuthentication == null ? storedRequest.getAuthorities() : userAuthentication.getAuthorities());
        this.storedRequest = storedRequest;
        this.userAuthentication = userAuthentication;
        if (userAuthentication != null) {
            this.setName(userAuthentication.getName());
        }
    }

    public boolean isClientOnly() {
        return userAuthentication == null;
    }

    public OAuth2Request getOAuth2Request() {
        return storedRequest;
    }

    public Authentication getUserAuthentication() {
        return userAuthentication;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return this.userAuthentication == null ? this.storedRequest.getClientId() : this.userAuthentication
                .getPrincipal();
    }
}
