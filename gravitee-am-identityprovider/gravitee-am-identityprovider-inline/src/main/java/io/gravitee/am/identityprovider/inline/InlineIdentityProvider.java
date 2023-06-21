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
package io.gravitee.am.identityprovider.inline;

import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.inline.authentication.InlineAuthenticationProvider;
import io.gravitee.plugin.core.api.Plugin;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InlineIdentityProvider extends IdentityProvider<InlineIdentityProviderConfiguration, InlineAuthenticationProvider> {

    @Override
    public Class<InlineIdentityProviderConfiguration> configuration() {
        return InlineIdentityProviderConfiguration.class;
    }

    @Override
    public Class<InlineAuthenticationProvider> provider() {
        return InlineAuthenticationProvider.class;
    }

}
