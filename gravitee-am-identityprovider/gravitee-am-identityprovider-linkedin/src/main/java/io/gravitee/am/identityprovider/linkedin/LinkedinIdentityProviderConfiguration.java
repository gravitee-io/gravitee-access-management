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
package io.gravitee.am.identityprovider.linkedin;

import io.gravitee.am.identityprovider.api.social.ProviderResponseType;
import io.gravitee.am.identityprovider.api.social.SocialIdentityProviderConfiguration;
import io.gravitee.secrets.api.annotation.Secret;
import lombok.Data;

import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
public class LinkedinIdentityProviderConfiguration implements SocialIdentityProviderConfiguration {

    private String userAuthorizationUri = "https://www.linkedin.com/oauth/v2/authorization";
    private String accessTokenUri = "https://www.linkedin.com/oauth/v2/accessToken";
    private String userProfileUri = "https://api.linkedin.com/v2/me?projection=(*,profilePicture(displayImage~:playableStreams))";
    private String userEmailAddressUri = "https://api.linkedin.com/v2/emailAddress?q=members&projection=(elements*(handle~))";
    private String codeParameter = "code";

    private String clientId;
    @Secret
    private String clientSecret;
    private Set<String> scopes;
    private Integer connectTimeout = 10000;
    private Integer idleTimeout = 10000;
    private Integer maxPoolSize = 200;



    @Override
    public ProviderResponseType getProviderResponseType() {
        return ProviderResponseType.CODE;
    }

    @Override
    public String getLogoutUri() {
        return null;
    }
}
