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
package io.gravitee.am.identityprovider.api.social;

import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;

import java.util.Set;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface SocialIdentityProviderConfiguration extends IdentityProviderConfiguration {

    public String getClientId();

    public String getClientSecret();

    public Set<String> getScopes();

    public String getUserAuthorizationUri();

    public String getAccessTokenUri();

    public String getLogoutUri();

    public String getUserProfileUri();

    public String getCodeParameter();

    public String getResponseType();

    public Integer getConnectTimeout();

    public Integer getIdleTimeout();

    public Integer getMaxPoolSize();

    default boolean isStoreOriginalTokens(){
        return false;
    }
}
