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
package io.gravitee.am.repository.mongodb.oauth2.utils;

import io.gravitee.am.model.oauth2.authority.GrantedAuthority;
import io.gravitee.am.model.oauth2.request.OAuth2Request;

import java.io.Serializable;
import java.util.*;

/**
 * Factory for tests to create OAuth2Request objects.
 *
 * @author Dave Syer
 *
 */
public class RequestTokenFactory {

    public static OAuth2Request createOAuth2Request(Map<String, String> requestParameters, String clientId,
                                                    Collection<? extends GrantedAuthority> authorities, boolean approved, Collection<String> scope,
                                                    Set<String> resourceIds, String redirectUri, Set<String> responseTypes,
                                                    Map<String, Serializable> extensionProperties) {
        return new OAuth2Request(requestParameters, clientId, authorities, approved, scope == null ? null
                : new LinkedHashSet<>(scope), resourceIds, redirectUri, responseTypes, extensionProperties);
    }

    public static OAuth2Request createOAuth2Request(String clientId, boolean approved) {
        return createOAuth2Request(clientId, approved, null);
    }

    public static OAuth2Request createOAuth2Request(String clientId, boolean approved, Collection<String> scope) {
        return createOAuth2Request(Collections.emptyMap(), clientId, approved, scope);
    }

    public static OAuth2Request createOAuth2Request(Map<String, String> parameters, String clientId, boolean approved,
                                                    Collection<String> scope) {
        return createOAuth2Request(parameters, clientId, null, approved, scope, null, null, null, null);
    }

}
