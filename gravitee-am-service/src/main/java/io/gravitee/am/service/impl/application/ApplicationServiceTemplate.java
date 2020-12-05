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
package io.gravitee.am.service.impl.application;

import io.gravitee.am.common.oauth2.ClientType;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;

import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationServiceTemplate implements ApplicationTemplate {

    @Override
    public boolean canHandle(Application application) {
        return ApplicationType.SERVICE.equals(application.getType());
    }

    /**
     * Backend/Service application are "confidential" client and need at least :
     *   - client_id
     *   - client_secret
     *   - authorized_grant_types : client_credentials
     */
    @Override
    public void handle(Application application) {
        // assign values
        update(application, false);
    }

    @Override
    public void changeType(Application application) {
        // force default values
        update(application, true);
    }

    private void update(Application application, boolean force) {
        // check for null values
        if (application.getSettings() == null) {
            application.setSettings(new ApplicationSettings());
        }
        if (application.getSettings().getOauth() == null) {
            application.getSettings().setOauth(new ApplicationOAuthSettings());
        }

        // assign values
        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        oAuthSettings.setClientId(oAuthSettings.getClientId() == null ? RandomString.generate() : oAuthSettings.getClientId());
        oAuthSettings.setClientSecret(oAuthSettings.getClientSecret() == null ? SecureRandomString.generate() : oAuthSettings.getClientSecret());
        oAuthSettings.setClientName(oAuthSettings.getClientName() == null ? application.getName() : oAuthSettings.getClientName());
        oAuthSettings.setClientType(ClientType.CONFIDENTIAL);
        oAuthSettings.setApplicationType(io.gravitee.am.common.oidc.ApplicationType.WEB);

        if (force || (oAuthSettings.getGrantTypes() == null || oAuthSettings.getGrantTypes().isEmpty())) {
            // service applications must have client_credentials
            oAuthSettings.setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
            oAuthSettings.setResponseTypes(Collections.emptyList());
            oAuthSettings.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        }
    }
}
