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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationWebTemplate extends ApplicationAbstractTemplate {

    @Override
    public boolean canHandle(Application application) {
        return ApplicationType.WEB.equals(application.getType());
    }

    @Override
    public void handle(Application application) {
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
        // we generate a client secret because a native application might use the password flow
        oAuthSettings.setClientSecret(oAuthSettings.getClientSecret() == null ? SecureRandomString.generate() : oAuthSettings.getClientSecret());
        oAuthSettings.setClientName(oAuthSettings.getClientName() == null ? application.getName() : oAuthSettings.getClientName());
        oAuthSettings.setClientType(ClientType.CONFIDENTIAL);
        oAuthSettings.setApplicationType(io.gravitee.am.common.oidc.ApplicationType.WEB);
        // web applications should have code and password flow
        if (oAuthSettings.getGrantTypes() == null || oAuthSettings.getGrantTypes().isEmpty()) {
            oAuthSettings.setGrantTypes(Arrays.asList(GrantType.AUTHORIZATION_CODE, GrantType.PASSWORD));
            oAuthSettings.setResponseTypes(new ArrayList<>(defaultAuthorizationCodeResponseTypes()));
        } else {
            Set<String> defaultResponseTypes = oAuthSettings.getResponseTypes() == null ? new HashSet<>() : new HashSet<>(oAuthSettings.getResponseTypes());
            if (oAuthSettings.getGrantTypes().contains(GrantType.AUTHORIZATION_CODE)) {
                // set default authorization code default response types
                if (!haveAuthorizationCodeResponseTypes(oAuthSettings.getResponseTypes())) {
                    defaultResponseTypes.addAll(defaultAuthorizationCodeResponseTypes());
                }
            } else {
                defaultResponseTypes.removeAll(defaultAuthorizationCodeResponseTypes());
            }
            if (oAuthSettings.getGrantTypes().contains(GrantType.IMPLICIT)) {
                // set default implicit default response types
                if (!haveImplicitResponseTypes(oAuthSettings.getResponseTypes())) {
                    defaultResponseTypes.addAll(defaultImplicitResponseTypes());
                }
            } else {
                defaultResponseTypes.removeAll(defaultImplicitResponseTypes());
            }
            oAuthSettings.setResponseTypes(new ArrayList<>(defaultResponseTypes));
        }
    }
}
