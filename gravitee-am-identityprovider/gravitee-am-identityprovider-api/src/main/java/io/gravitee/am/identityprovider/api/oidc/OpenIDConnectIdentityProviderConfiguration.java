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
package io.gravitee.am.identityprovider.api.oidc;

import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.common.oauth2.CodeChallengeMethod;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.identityprovider.api.oidc.jwt.KeyResolver;
import io.gravitee.am.identityprovider.api.social.SocialIdentityProviderConfiguration;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface OpenIDConnectIdentityProviderConfiguration extends SocialIdentityProviderConfiguration {
    String getWellKnownUri();

    boolean isUseIdTokenForUserInfo();

    default boolean doesUserInfoProvideJwt() {
        return false;
    }

    KeyResolver getPublicKeyResolver();

    SignatureAlgorithm getSignatureAlgorithm();

    String getResolverParameter();

    boolean isEncodeRedirectUri();

    default String getClientAuthenticationMethod() {
        return ClientAuthenticationMethod.CLIENT_SECRET_POST;
    }

    /**
     * Type of challenge used for PKCE. null means PKCE shouldn't be used
     */
    default CodeChallengeMethod getCodeChallengeMethod() {
        return null;
    }

    default boolean usePkce() {
        return getCodeChallengeMethod() != null;
    }
}
