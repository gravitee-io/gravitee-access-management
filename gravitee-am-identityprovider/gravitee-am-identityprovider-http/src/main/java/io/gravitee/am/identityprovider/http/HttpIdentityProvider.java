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
package io.gravitee.am.identityprovider.http;

import io.gravitee.am.identityprovider.api.IdentityProvider;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.identityprovider.http.authentication.HttpAuthenticationProvider;
import io.gravitee.am.identityprovider.http.configuration.HttpIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.http.user.HttpUserProvider;
import io.gravitee.plugin.core.api.Plugin;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HttpIdentityProvider extends IdentityProvider<HttpIdentityProviderConfiguration, HttpAuthenticationProvider> {

    @Override
    public Class<HttpIdentityProviderConfiguration> configuration() {
        return HttpIdentityProviderConfiguration.class;
    }

    @Override
    public Class<HttpAuthenticationProvider> provider() {
        return HttpAuthenticationProvider.class;
    }

    @Override
    public Class<? extends UserProvider> userProvider() {
        return HttpUserProvider.class;
    }

}
