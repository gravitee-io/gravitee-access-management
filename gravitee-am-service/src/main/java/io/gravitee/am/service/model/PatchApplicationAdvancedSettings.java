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
package io.gravitee.am.service.model;

import io.gravitee.am.model.application.ApplicationAdvancedSettings;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchApplicationAdvancedSettings {

    private Optional<Boolean> skipConsent;
    private Optional<Boolean> flowsInherited;
    private Optional<String> agentCardUrl;

    public Optional<Boolean> getSkipConsent() {
        return skipConsent;
    }

    public void setSkipConsent(Optional<Boolean> skipConsent) {
        this.skipConsent = skipConsent;
    }

    public Optional<Boolean> getFlowsInherited() {
        return flowsInherited;
    }

    public void setFlowsInherited(Optional<Boolean> flowsInherited) {
        this.flowsInherited = flowsInherited;
    }

    public Optional<String> getAgentCardUrl() {
        return agentCardUrl;
    }

    public void setAgentCardUrl(Optional<String> agentCardUrl) {
        this.agentCardUrl = agentCardUrl;
    }

    public ApplicationAdvancedSettings patch(ApplicationAdvancedSettings _toPatch) {
        // create new object for audit purpose (patch json result)
        ApplicationAdvancedSettings toPatch = _toPatch == null ? new ApplicationAdvancedSettings() : new ApplicationAdvancedSettings(_toPatch);
        SetterUtils.safeSet(toPatch::setSkipConsent, this.getSkipConsent(), boolean.class);
        SetterUtils.safeSet(toPatch::setFlowsInherited, this.getFlowsInherited(), boolean.class);
        SetterUtils.safeSet(toPatch::setAgentCardUrl, this.getAgentCardUrl(), String.class);

        return toPatch;
    }

}
