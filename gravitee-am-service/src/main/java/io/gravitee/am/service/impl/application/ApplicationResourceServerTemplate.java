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

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.application.ApplicationType;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * A server that hosts resources on a resource owner's behalf and is capable of accepting and responding to requests for protected resources.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationResourceServerTemplate extends ApplicationWebTemplate {

    @Override
    public boolean canHandle(Application application) {
        return ApplicationType.RESOURCE_SERVER.equals(application.getType());
    }

    @Override
    public void handle(Application application) {
        // assign values
        super.handle(application);
        update(application);
    }

    @Override
    public void changeType(Application application) {
        // force default values
        super.changeType(application);
        update(application);
    }

    private void update(Application application) {
        // UMA resource server should at least has the uma_protection scope
        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        if (oAuthSettings.getScopeSettings() == null) {
            oAuthSettings.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings(Scope.UMA.getKey())));
        } else if (oAuthSettings.getScopeSettings().stream().filter(s -> s.getScope().equals(Scope.UMA.getKey())).findFirst().isEmpty()) {
            oAuthSettings.getScopeSettings().add(new ApplicationScopeSettings(Scope.UMA.getKey()));
        }
        // UMA resource server should at least has the client_credentials grant to request permissions
        if(oAuthSettings.getGrantTypes() == null) {
            oAuthSettings.setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
        } else if (!oAuthSettings.getGrantTypes().contains(GrantType.CLIENT_CREDENTIALS)) {
            List<String> grants = new LinkedList<>(oAuthSettings.getGrantTypes());
            grants.add(GrantType.CLIENT_CREDENTIALS);
            oAuthSettings.setGrantTypes(grants);
        }
    }
}
