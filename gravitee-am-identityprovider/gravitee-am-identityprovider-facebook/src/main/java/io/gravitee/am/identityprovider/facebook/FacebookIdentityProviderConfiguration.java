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
package io.gravitee.am.identityprovider.facebook;

import io.gravitee.am.identityprovider.api.social.ProviderResponseType;
import io.gravitee.am.identityprovider.api.social.SocialIdentityProviderConfiguration;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;

import java.util.Set;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
public class FacebookIdentityProviderConfiguration implements SocialIdentityProviderConfiguration {

    private static final String FACEBOOK_AUTH_BASE = "https://www.facebook.com";
    private static final String GRAPH_API_BASE = "https://graph.facebook.com";

    // Default to v8.0 for backward compatibility with existing configurations that lack this field.
    // The schema form sets "default": "v24.0" so new IDPs created via the UI will use v24.0.
    private String apiVersion = "v8.0";

    // URL fields kept for backward-compatible deserialization; getters compute from apiVersion.
    @Getter(AccessLevel.NONE)
    private String userAuthorizationUri;
    @Getter(AccessLevel.NONE)
    private String accessTokenUri;
    @Getter(AccessLevel.NONE)
    private String userProfileUri;

    private String codeParameter = "code";

    private String clientId;
    private String clientSecret;
    private Set<String> scopes;
    private Integer connectTimeout = 10000;
    private Integer idleTimeout = 10000;
    private Integer maxPoolSize = 200;

    public String getUserAuthorizationUri() {
        return FACEBOOK_AUTH_BASE + "/" + apiVersion + "/dialog/oauth";
    }

    public String getAccessTokenUri() {
        return GRAPH_API_BASE + "/" + apiVersion + "/oauth/access_token";
    }

    public String getUserProfileUri() {
        return GRAPH_API_BASE + "/" + apiVersion + "/me";
    }

    @Override
    public ProviderResponseType getProviderResponseType() {
        return ProviderResponseType.CODE;
    }

    @Override
    public String getLogoutUri() {
        return null;
    }

}
