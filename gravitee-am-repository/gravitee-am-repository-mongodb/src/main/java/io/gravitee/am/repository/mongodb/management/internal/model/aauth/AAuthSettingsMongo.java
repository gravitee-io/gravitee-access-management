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
package io.gravitee.am.repository.mongodb.management.internal.model.aauth;

import java.util.List;

/**
 * MongoDB representation of AAUTH protocol settings.
 *
 * @author GraviteeSource Team
 */
public class AAuthSettingsMongo {

    private boolean enabled;
    private int authTokenLifespan;
    private int pendingRequestTtl;
    private List<String> allowedAgentPatterns;
    private List<String> trustedResourcePatterns;
    private boolean autoRegisterAgents;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAuthTokenLifespan() {
        return authTokenLifespan;
    }

    public void setAuthTokenLifespan(int authTokenLifespan) {
        this.authTokenLifespan = authTokenLifespan;
    }

    public int getPendingRequestTtl() {
        return pendingRequestTtl;
    }

    public void setPendingRequestTtl(int pendingRequestTtl) {
        this.pendingRequestTtl = pendingRequestTtl;
    }

    public List<String> getAllowedAgentPatterns() {
        return allowedAgentPatterns;
    }

    public void setAllowedAgentPatterns(List<String> allowedAgentPatterns) {
        this.allowedAgentPatterns = allowedAgentPatterns;
    }

    public List<String> getTrustedResourcePatterns() {
        return trustedResourcePatterns;
    }

    public void setTrustedResourcePatterns(List<String> trustedResourcePatterns) {
        this.trustedResourcePatterns = trustedResourcePatterns;
    }

    public boolean isAutoRegisterAgents() {
        return autoRegisterAgents;
    }

    public void setAutoRegisterAgents(boolean autoRegisterAgents) {
        this.autoRegisterAgents = autoRegisterAgents;
    }
}
