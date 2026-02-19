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
package io.gravitee.am.repository.mongodb.management.internal.model;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationAdvancedSettingsMongo {

    private boolean skipConsent;
    private boolean flowsInherited;
    private String mfaSelectionRule;
    private String agentCardUrl;

    public boolean isSkipConsent() {
        return skipConsent;
    }

    public void setSkipConsent(boolean skipConsent) {
        this.skipConsent = skipConsent;
    }

    public boolean isFlowsInherited() {
        return flowsInherited;
    }

    public void setFlowsInherited(boolean flowsInherited) {
        this.flowsInherited = flowsInherited;
    }

    public String getMfaSelectionRule() {
        return mfaSelectionRule;
    }

    public void setMfaSelectionRule(String mfaSelectionRule) {
        this.mfaSelectionRule = mfaSelectionRule;
    }

    public String getAgentCardUrl() {
        return agentCardUrl;
    }

    public void setAgentCardUrl(String agentCardUrl) {
        this.agentCardUrl = agentCardUrl;
    }
}
