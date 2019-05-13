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
package io.gravitee.am.identityprovider.api.oauth2;

import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;

import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface OAuth2IdentityProviderConfiguration extends IdentityProviderConfiguration {

    String getClientId();

    String getClientSecret();

    String getUserAuthorizationUri();

    String getAccessTokenUri();

    String getUserProfileUri();

    String getCodeParameter();

    Set<String> getScopes();

    default String getResponseType() {
        return ResponseType.CODE;
    }

}
