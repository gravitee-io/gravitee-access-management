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
import io.gravitee.am.service.impl.application.ApplicationServiceTemplate;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationServiceTemplateTest {

    private ApplicationServiceTemplate applicationServiceTemplate = new ApplicationServiceTemplate();

    @Test
    public void shouldApply_noGrantType() {
        Application application = new Application();
        application.setType(ApplicationType.SERVICE);
        application.setName("app-name");

        applicationServiceTemplate.handle(application);

        Assert.assertNotNull(application);
        Assert.assertNotNull(application.getSettings());
        Assert.assertNotNull(application.getSettings().getOauth());

        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        Assert.assertTrue(oAuthSettings.getClientId() != null && !oAuthSettings.getClientId().isEmpty());
        Assert.assertTrue(oAuthSettings.getClientSecret() != null && !oAuthSettings.getClientSecret().isEmpty());
        Assert.assertTrue(oAuthSettings.getGrantTypes().contains(GrantType.CLIENT_CREDENTIALS));
        Assert.assertTrue(oAuthSettings.getResponseTypes().isEmpty());
    }

    @Test
    public void shouldApply_existingGrantType() {
        Application application = new Application();
        application.setType(ApplicationType.SERVICE);
        application.setName("app-name");

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Collections.singletonList(GrantType.PASSWORD));
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
        Assert.assertTrue(oAuthSettings1.getGrantTypes().size() == 1 && oAuthSettings1.getGrantTypes().contains(GrantType.PASSWORD));
    }
}
