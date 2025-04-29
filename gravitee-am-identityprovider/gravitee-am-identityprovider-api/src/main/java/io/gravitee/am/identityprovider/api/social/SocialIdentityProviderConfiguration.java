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

import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;

import java.util.Set;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface SocialIdentityProviderConfiguration extends IdentityProviderConfiguration {
    String getClientId();

    String getClientSecret();

    Set<String> getScopes();

    String getUserAuthorizationUri();

    String getAccessTokenUri();

    String getLogoutUri();

    String getUserProfileUri();

    String getCodeParameter();

    /**
     * @deprecated implement {@link #getProviderResponseType()} instead
     */
    @Deprecated(since="4.7.0")
    default String getResponseType() {
        return getProviderResponseType().value();
    }

    default ProviderResponseType getProviderResponseType() {
        return ProviderResponseType.CODE;
    }

    default ProviderResponseMode getResponseMode() {
        if (getResponseType().equals(ResponseType.TOKEN)) {
            return ProviderResponseMode.FRAGMENT;
        } else {
            return ProviderResponseMode.QUERY;
        }
    }

    Integer getConnectTimeout();

    Integer getIdleTimeout();

    Integer getMaxPoolSize();

    default boolean isStoreOriginalTokens(){
        return false;
    }
}
