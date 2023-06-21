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
package io.gravitee.am.identityprovider.jdbc;

import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.IdentityProvider;
import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.identityprovider.jdbc.authentication.JdbcAuthenticationProvider;
import io.gravitee.am.identityprovider.jdbc.configuration.JdbcIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.jdbc.user.JdbcUserProvider;
import io.gravitee.plugin.core.api.Plugin;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JdbcIdentityProvider extends IdentityProvider<JdbcIdentityProviderConfiguration, JdbcAuthenticationProvider> {

    @Override
    public Class<JdbcIdentityProviderConfiguration> configuration() {
        return JdbcIdentityProviderConfiguration.class;
    }

    @Override
    public Class<JdbcAuthenticationProvider> provider() {
        return JdbcAuthenticationProvider.class;
    }

    @Override
    public Class<? extends UserProvider> userProvider() {
        return JdbcUserProvider.class;
    }

}
