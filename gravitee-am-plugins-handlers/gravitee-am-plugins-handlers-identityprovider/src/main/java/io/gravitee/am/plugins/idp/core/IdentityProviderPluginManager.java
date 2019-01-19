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
package io.gravitee.am.plugins.idp.core;

import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.plugin.core.api.Plugin;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface IdentityProviderPluginManager {

    void register(IdentityProviderDefinition identityProviderPluginDefinition, boolean oauth2Provider);

    Collection<Plugin> getAll();

    Collection<Plugin> getOAuth2Providers();

    Plugin findById(String identityProviderId);

    AuthenticationProvider create(String type, String configuration, Map<String, String> mappers, Map<String, String[]> roleMapper);

    UserProvider create(String type, String configuration);

    String getSchema(String identityProviderId) throws IOException;
}
