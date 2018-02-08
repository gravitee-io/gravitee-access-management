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

import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;
import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2IdentityProvider;
import io.gravitee.am.identityprovider.oauth2.authentication.OAuth2GenericAuthenticationProvider;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2GenericIdentityProvider implements OAuth2IdentityProvider {

    @Override
    public Class<? extends IdentityProviderConfiguration> configuration() {
        return OAuth2GenericIdentityProviderConfiguration.class;
    }

    @Override
    public Class<? extends AuthenticationProvider> authenticationProvider() {
        return OAuth2GenericAuthenticationProvider.class;
    }

    @Override
    public Class<? extends IdentityProviderMapper> mapper() {
        return OAuth2GenericIdentityProviderMapper.class;
    }

    @Override
    public Class<? extends IdentityProviderRoleMapper> roleMapper() {
        return OAuth2GenericIdentityProviderRoleMapper.class;
    }
}
