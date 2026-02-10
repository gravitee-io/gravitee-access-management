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
package io.gravitee.am.service.application;

import io.gravitee.am.common.oauth2.ClientType;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.service.impl.application.ApplicationAgentTemplate;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.gravitee.am.common.oauth2.ResponseType.CODE;
import static io.gravitee.am.common.oauth2.ResponseType.TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN_TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.CODE_TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.ID_TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.ID_TOKEN_TOKEN;

/**
 * @author GraviteeSource Team
 */
public class ApplicationAgentTemplateTest {

    private ApplicationAgentTemplate applicationAgentTemplate = new ApplicationAgentTemplate();

    @Test
    public void shouldApply_noGrantType() {
        Application application = new Application();
        application.setType(ApplicationType.AGENT);
        application.setName("app-name");

        applicationAgentTemplate.handle(application);

        Assert.assertNotNull(application);
        Assert.assertNotNull(application.getSettings());
        Assert.assertNotNull(application.getSettings().getOauth());

        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        Assert.assertTrue(oAuthSettings.getClientId() != null && !oAuthSettings.getClientId().isEmpty());
        Assert.assertTrue(oAuthSettings.getClientSecret() != null && !oAuthSettings.getClientSecret().isEmpty());
        Assert.assertEquals(1, oAuthSettings.getGrantTypes().size());
        Assert.assertTrue(oAuthSettings.getGrantTypes().contains(GrantType.AUTHORIZATION_CODE));
        Assert.assertEquals(ClientType.CONFIDENTIAL, oAuthSettings.getClientType());
        Assert.assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, oAuthSettings.getTokenEndpointAuthMethod());
        List<String> responseTypes = new ArrayList<>(defaultAuthorizationCodeResponseTypes());
        Assert.assertTrue(oAuthSettings.getResponseTypes().containsAll(responseTypes));
    }

    @Test
    public void shouldApply_existingGrantType() {
        Application application = new Application();
        application.setType(ApplicationType.AGENT);
        application.setName("app-name");

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList(GrantType.AUTHORIZATION_CODE, GrantType.CLIENT_CREDENTIALS));
        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);
        application.setSettings(settings);

        applicationAgentTemplate.handle(application);

        Assert.assertNotNull(application);
        Assert.assertNotNull(application.getSettings());
        Assert.assertNotNull(application.getSettings().getOauth());

        ApplicationOAuthSettings oAuthSettings1 = application.getSettings().getOauth();
        Assert.assertTrue(oAuthSettings1.getClientId() != null && !oAuthSettings1.getClientId().isEmpty());
        Assert.assertTrue(oAuthSettings1.getClientSecret() != null && !oAuthSettings1.getClientSecret().isEmpty());
        Assert.assertEquals(2, oAuthSettings1.getGrantTypes().size());
        Assert.assertTrue(oAuthSettings1.getGrantTypes().containsAll(Arrays.asList(GrantType.AUTHORIZATION_CODE, GrantType.CLIENT_CREDENTIALS)));
        List<String> responseTypes = new ArrayList<>(defaultAuthorizationCodeResponseTypes());
        Assert.assertTrue(oAuthSettings1.getResponseTypes().containsAll(responseTypes));
    }

    @Test
    public void shouldApply_existingGrantType_stripsForbiddenGrants() {
        Application application = new Application();
        application.setType(ApplicationType.AGENT);
        application.setName("app-name");

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList(
                GrantType.AUTHORIZATION_CODE, GrantType.IMPLICIT, GrantType.PASSWORD, GrantType.REFRESH_TOKEN, GrantType.CLIENT_CREDENTIALS));
        oAuthSettings.setResponseTypes(new ArrayList<>(defaultAuthorizationCodeResponseTypes()));
        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);
        application.setSettings(settings);

        applicationAgentTemplate.handle(application);

        ApplicationOAuthSettings result = application.getSettings().getOauth();
        Assert.assertEquals(2, result.getGrantTypes().size());
        Assert.assertTrue(result.getGrantTypes().contains(GrantType.AUTHORIZATION_CODE));
        Assert.assertTrue(result.getGrantTypes().contains(GrantType.CLIENT_CREDENTIALS));
        Assert.assertFalse(result.getGrantTypes().contains(GrantType.IMPLICIT));
        Assert.assertFalse(result.getGrantTypes().contains(GrantType.PASSWORD));
        Assert.assertFalse(result.getGrantTypes().contains(GrantType.REFRESH_TOKEN));
    }

    @Test
    public void shouldApply_existingGrantType_removesImplicitResponseTypes() {
        Application application = new Application();
        application.setType(ApplicationType.AGENT);
        application.setName("app-name");

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList(GrantType.AUTHORIZATION_CODE, GrantType.IMPLICIT));
        Set<String> allResponseTypes = new HashSet<>();
        allResponseTypes.addAll(defaultAuthorizationCodeResponseTypes());
        allResponseTypes.addAll(defaultImplicitResponseTypes());
        oAuthSettings.setResponseTypes(new ArrayList<>(allResponseTypes));
        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);
        application.setSettings(settings);

        applicationAgentTemplate.handle(application);

        ApplicationOAuthSettings result = application.getSettings().getOauth();
        // Implicit response types should be removed
        for (String implicitResponseType : defaultImplicitResponseTypes()) {
            Assert.assertFalse("Should not contain implicit response type: " + implicitResponseType,
                    result.getResponseTypes().contains(implicitResponseType));
        }
        // Authorization code response types should remain
        Assert.assertTrue(result.getResponseTypes().containsAll(defaultAuthorizationCodeResponseTypes()));
    }

    @Test
    public void shouldChangeType() {
        Application application = new Application();
        application.setType(ApplicationType.AGENT);
        application.setName("app-name");

        ApplicationOAuthSettings existingOAuthSettings = new ApplicationOAuthSettings();
        existingOAuthSettings.setGrantTypes(Arrays.asList(GrantType.IMPLICIT, GrantType.PASSWORD, GrantType.REFRESH_TOKEN));
        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(existingOAuthSettings);
        application.setSettings(settings);

        applicationAgentTemplate.changeType(application);

        Assert.assertNotNull(application);
        Assert.assertNotNull(application.getSettings());
        Assert.assertNotNull(application.getSettings().getOauth());

        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        Assert.assertTrue(oAuthSettings.getClientId() != null && !oAuthSettings.getClientId().isEmpty());
        Assert.assertTrue(oAuthSettings.getClientSecret() != null && !oAuthSettings.getClientSecret().isEmpty());
        Assert.assertEquals(1, oAuthSettings.getGrantTypes().size());
        Assert.assertTrue(oAuthSettings.getGrantTypes().contains(GrantType.AUTHORIZATION_CODE));
        Assert.assertFalse(oAuthSettings.getGrantTypes().contains(GrantType.IMPLICIT));
        Assert.assertFalse(oAuthSettings.getGrantTypes().contains(GrantType.PASSWORD));
        Assert.assertFalse(oAuthSettings.getGrantTypes().contains(GrantType.REFRESH_TOKEN));
        Assert.assertEquals(ClientType.CONFIDENTIAL, oAuthSettings.getClientType());
        Assert.assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, oAuthSettings.getTokenEndpointAuthMethod());
        List<String> responseTypes = new ArrayList<>(defaultAuthorizationCodeResponseTypes());
        Assert.assertTrue(oAuthSettings.getResponseTypes().containsAll(responseTypes));
    }

    private static Set<String> defaultAuthorizationCodeResponseTypes() {
        return new HashSet<>(Arrays.asList(CODE, CODE_TOKEN, CODE_ID_TOKEN, CODE_ID_TOKEN_TOKEN));
    }

    private static Set<String> defaultImplicitResponseTypes() {
        return new HashSet<>(Arrays.asList(TOKEN, ID_TOKEN, ID_TOKEN_TOKEN));
    }
}
