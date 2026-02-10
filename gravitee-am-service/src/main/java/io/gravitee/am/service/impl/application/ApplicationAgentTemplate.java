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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author GraviteeSource Team
 */
public class ApplicationAgentTemplate extends ApplicationAbstractTemplate {

    private static final List<String> FORBIDDEN_GRANT_TYPES = Arrays.asList(
            GrantType.IMPLICIT, GrantType.PASSWORD, GrantType.REFRESH_TOKEN
    );

    @Override
    public boolean canHandle(Application application) {
        return ApplicationType.AGENT.equals(application.getType());
    }

    @Override
    public void handle(Application application) {
        update(application, false);
    }

    @Override
    public void changeType(Application application) {
        update(application, true);
    }

    private void update(Application application, boolean force) {
        if (application.getSettings() == null) {
            application.setSettings(new ApplicationSettings());
        }
        if (application.getSettings().getOauth() == null) {
            application.getSettings().setOauth(new ApplicationOAuthSettings());
        }

        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        oAuthSettings.setClientId(oAuthSettings.getClientId() == null ? RandomString.generate() : oAuthSettings.getClientId());
        oAuthSettings.setClientSecret(oAuthSettings.getClientSecret() == null ? SecureRandomString.generate() : oAuthSettings.getClientSecret());
        oAuthSettings.setClientName(oAuthSettings.getClientName() == null ? application.getName() : oAuthSettings.getClientName());
        oAuthSettings.setClientType(ClientType.CONFIDENTIAL);
        oAuthSettings.setApplicationType(io.gravitee.am.common.oidc.ApplicationType.WEB);
        if (force || (oAuthSettings.getGrantTypes() == null || oAuthSettings.getGrantTypes().isEmpty())) {
            oAuthSettings.setGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
            oAuthSettings.setResponseTypes(new ArrayList<>(defaultAuthorizationCodeResponseTypes()));
            oAuthSettings.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        } else {
            // Strip forbidden grant types
            List<String> grantTypes = new ArrayList<>(oAuthSettings.getGrantTypes());
            grantTypes.removeAll(FORBIDDEN_GRANT_TYPES);
            oAuthSettings.setGrantTypes(grantTypes);

            Set<String> defaultResponseTypes = oAuthSettings.getResponseTypes() == null ? new HashSet<>() : new HashSet<>(oAuthSettings.getResponseTypes());
            if (oAuthSettings.getGrantTypes().contains(GrantType.AUTHORIZATION_CODE)) {
                if (!haveAuthorizationCodeResponseTypes(oAuthSettings.getResponseTypes())) {
                    defaultResponseTypes.addAll(defaultAuthorizationCodeResponseTypes());
                }
            } else {
                defaultResponseTypes.removeAll(defaultAuthorizationCodeResponseTypes());
            }
            // Always remove implicit response types for agent applications
            defaultResponseTypes.removeAll(defaultImplicitResponseTypes());
            oAuthSettings.setResponseTypes(new ArrayList<>(defaultResponseTypes));
        }
    }
}
