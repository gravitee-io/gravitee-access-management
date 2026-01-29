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
package io.gravitee.am.identityprovider.oauth2;

import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.common.oauth2.CodeChallengeMethod;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.api.oidc.jwt.KeyResolver;
import io.gravitee.am.identityprovider.api.social.ProviderResponseMode;
import io.gravitee.am.identityprovider.api.social.ProviderResponseType;
import io.gravitee.am.identityprovider.oauth2.jwt.algo.Signature;
import io.gravitee.secrets.api.annotation.Secret;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class OAuth2GenericIdentityProviderConfiguration implements OpenIDConnectIdentityProviderConfiguration {

    private static final String CODE_PARAMETER = "code";
    private String clientId;
    @Secret
    private String clientSecret;
    private String wellKnownUri;
    private String userAuthorizationUri;
    private String accessTokenUri;
    private String userProfileUri;
    private String logoutUri;
    private Set<String> scopes;
    private ProviderResponseType responseType;
    private ProviderResponseMode responseMode = ProviderResponseMode.DEFAULT;
    private boolean useIdTokenForUserInfo;
    private Signature signature = Signature.RSA_RS256;
    private KeyResolver publicKeyResolver;
    private String resolverParameter;
    private boolean encodeRedirectUri;
    private Integer connectTimeout = 10000;
    private Integer idleTimeout = 10000;
    private Integer maxPoolSize = 200;
    private String clientAuthenticationMethod = ClientAuthenticationMethod.CLIENT_SECRET_POST;
    private String clientAuthenticationCertificate;
    private boolean storeOriginalTokens;
    private CodeChallengeMethod codeChallengeMethod;
    private boolean userInfoAsJwt = false;
    @Override
    public String getResponseType() {
        return responseType.value();
    }

    @Override
    public ProviderResponseType getProviderResponseType() {
        return responseType;
    }


    @Override
    public ProviderResponseMode getResponseMode() {
        if (responseMode == null || responseMode == ProviderResponseMode.DEFAULT) {
            return getProviderResponseType().defaultResponseMode();
        } else {
            return responseMode;
        }
    }

    public String getCodeParameter() {
        return CODE_PARAMETER;
    }

    @Override
    public boolean isStoreOriginalTokens() {
        return storeOriginalTokens;
    }

    @Override
    public SignatureAlgorithm getSignatureAlgorithm() {
        return this.signature == null ? null : this.signature.getAlg();
    }

    @Override
    public String getLogoutUri() {
        return this.logoutUri;
    }

    @Override
    public String getClientAuthenticationMethod() {
        return clientAuthenticationMethod;
    }

    @Override
    public boolean doesUserInfoProvideJwt() {
        return this.userInfoAsJwt;
    }
}
