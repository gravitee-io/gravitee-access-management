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

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;

public class ApplicationAgentTemplate implements ApplicationTemplate {

    @Override
    public boolean canHandle(Application application) {
        return ApplicationType.AGENT.equals(application.getType());
    }

    @Override
    public void handle(Application application) {
        update(application);
    }

    @Override
    public void changeType(Application application) {
        update(application);
    }

    private void update(Application application) {
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
    }
}
