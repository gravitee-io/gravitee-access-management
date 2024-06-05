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

import io.gravitee.am.identityprovider.api.social.SocialIdentityProvider;
import io.gravitee.am.identityprovider.oauth2.authentication.OAuth2GenericAuthenticationProvider;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2GenericIdentityProvider extends SocialIdentityProvider<OAuth2GenericIdentityProviderConfiguration, OAuth2GenericAuthenticationProvider> {

    @Override
    public Class<OAuth2GenericIdentityProviderConfiguration> configuration() {
        return OAuth2GenericIdentityProviderConfiguration.class;
    }

    @Override
    public Class<OAuth2GenericAuthenticationProvider> provider() {
        return OAuth2GenericAuthenticationProvider.class;
    }
}
