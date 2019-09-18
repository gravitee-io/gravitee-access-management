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

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.service.impl.application.ApplicationNativeTemplate;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

import static io.gravitee.am.common.oauth2.ResponseType.CODE;
import static io.gravitee.am.common.oauth2.ResponseType.TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationNativeTemplateTest {

    private ApplicationNativeTemplate applicationServiceTemplate = new ApplicationNativeTemplate();

    @Test
    public void shouldApply_noGrantType() {
        Application application = new Application();
        application.setType(ApplicationType.NATIVE);
        application.setName("app-name");

        applicationServiceTemplate.handle(application);

        Assert.assertNotNull(application);
        Assert.assertNotNull(application.getSettings());
        Assert.assertNotNull(application.getSettings().getOauth());

        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        Assert.assertTrue(oAuthSettings.getClientId() != null && !oAuthSettings.getClientId().isEmpty());
        Assert.assertTrue(oAuthSettings.getClientSecret() != null && !oAuthSettings.getClientSecret().isEmpty());
        Assert.assertTrue(oAuthSettings.getGrantTypes().containsAll(Arrays.asList(GrantType.AUTHORIZATION_CODE, GrantType.IMPLICIT, GrantType.PASSWORD)));
        List<String> responseTypes = new ArrayList<>(defaultAuthorizationCodeResponseTypes());
        responseTypes.addAll(defaultImplicitResponseTypes());
        Assert.assertTrue(oAuthSettings.getResponseTypes().containsAll(responseTypes));
    }

    @Test
    public void shouldApply_existingGrantType() {
        Application application = new Application();
        application.setType(ApplicationType.NATIVE);
        application.setName("app-name");

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList(GrantType.AUTHORIZATION_CODE, GrantType.IMPLICIT, GrantType.REFRESH_TOKEN));
        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);
        application.setSettings(settings);

        applicationServiceTemplate.handle(application);

        Assert.assertNotNull(application);
        Assert.assertNotNull(application.getSettings());
        Assert.assertNotNull(application.getSettings().getOauth());

        ApplicationOAuthSettings oAuthSettings1 = application.getSettings().getOauth();
        Assert.assertTrue(oAuthSettings1.getClientId() != null && !oAuthSettings.getClientId().isEmpty());
        Assert.assertTrue(oAuthSettings1.getClientSecret() != null && !oAuthSettings.getClientSecret().isEmpty());
        Assert.assertTrue(oAuthSettings1.getGrantTypes().size() == 3 && oAuthSettings1.getGrantTypes().containsAll(Arrays.asList(GrantType.AUTHORIZATION_CODE, GrantType.IMPLICIT, GrantType.REFRESH_TOKEN)));
        List<String> responseTypes = new ArrayList<>(defaultAuthorizationCodeResponseTypes());
        responseTypes.addAll(defaultImplicitResponseTypes());
        Assert.assertTrue(oAuthSettings1.getResponseTypes().containsAll(responseTypes));
    }

    private static Set<String> defaultAuthorizationCodeResponseTypes() {
        return new HashSet<>(Arrays.asList(CODE, CODE_TOKEN, CODE_ID_TOKEN, CODE_ID_TOKEN_TOKEN));
    }

    private static Set<String> defaultImplicitResponseTypes() {
        return new HashSet<>(Arrays.asList(TOKEN, ID_TOKEN, ID_TOKEN_TOKEN));
    }
}
