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

import io.gravitee.am.certificate.api.CertificateManager;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import java.util.Map;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationProviderConfiguration extends ProviderConfiguration {

   private final Map<String, String> mappers;
   private final Map<String, String[]> roleMapper;
   private final CertificateManager certificateManager;

   private final IdentityProvider identityProvider;

    public AuthenticationProviderConfiguration(IdentityProvider identityProvider) {
        this(identityProvider, null);
    }

    public AuthenticationProviderConfiguration(IdentityProvider identityProvider, CertificateManager certificateManager) {
        super(identityProvider.getType(), identityProvider.getConfiguration());
        this.mappers = identityProvider.getMappers();
        this.roleMapper = identityProvider.getRoleMapper();
        this.certificateManager = certificateManager;
        this.identityProvider = identityProvider;
    }

    public Map<String, String> getMappers() {
        return mappers;
    }

    public Map<String, String[]> getRoleMapper() {
        return roleMapper;
    }

    public CertificateManager getCertificateManager() {
        return certificateManager;
    }

    public IdentityProvider getIdentityProvider() {
        return identityProvider;
    }
}
