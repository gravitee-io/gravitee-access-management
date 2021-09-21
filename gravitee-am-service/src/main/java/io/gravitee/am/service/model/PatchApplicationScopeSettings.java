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

import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchApplicationScopeSettings {
    private Optional<String> scope;
    /**
     * True if the scope is used as default scope
     */
    private Optional<Boolean> defaultScope;
    /**
     * Scope approval duration times
     */
    private Optional<Integer> scopeApproval;

    public Optional<String> getScope() {
        return scope;
    }

    public void setScope(Optional<String> scope) {
        this.scope = scope;
    }

    public Optional<Boolean> getDefaultScope() {
        return defaultScope;
    }

    public void setDefaultScope(Optional<Boolean> defaultScope) {
        this.defaultScope = defaultScope;
    }

    public Optional<Integer> getScopeApproval() {
        return scopeApproval;
    }

    public void setScopeApproval(Optional<Integer> scopeApproval) {
        this.scopeApproval = scopeApproval;
    }

    public ApplicationScopeSettings patch(ApplicationScopeSettings _toPatch) {
        ApplicationScopeSettings toPatch = _toPatch == null ? new ApplicationScopeSettings() : new ApplicationScopeSettings(_toPatch);

        SetterUtils.safeSet(toPatch::setScope, this.getScope());
        SetterUtils.safeSet(toPatch::setDefaultScope, this.getDefaultScope());
        SetterUtils.safeSet(toPatch::setScopeApproval, this.getScopeApproval());

        return toPatch;
    }
}
