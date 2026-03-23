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
package io.gravitee.am.plugins.idp.core.impl;

import io.gravitee.am.certificate.api.CertificateManager;
import io.gravitee.am.common.plugin.ValidationResult;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.plugins.idp.core.AuthenticationProviderConfiguration;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.service.validators.idp.IdentityProviderPluginValidator;
import lombok.RequiredArgsConstructor;

/**
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class IdentityProviderPluginValidatorImpl implements IdentityProviderPluginValidator {

    private final IdentityProviderPluginManager identityProviderPluginManager;
    private final CertificateManager certificateManager;

    @Override
    public ValidationResult validate(String type, String configuration) {
        var idp = new IdentityProvider();
        idp.setType(type);
        idp.setConfiguration(configuration);
        return identityProviderPluginManager.validate(new AuthenticationProviderConfiguration(idp, certificateManager));
    }
}
