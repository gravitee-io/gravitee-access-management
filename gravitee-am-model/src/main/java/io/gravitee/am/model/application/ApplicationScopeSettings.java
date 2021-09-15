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
package io.gravitee.am.model.application;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationScopeSettings {
    private String scope;
    /**
     * True if the scope is used as default scope
     */
    private boolean defaultScope;
    /**
     * True if the scope maybe parameterized (ie: the scope asked by the client may have a suffix)
     */
    private boolean parameterized;
    /**
     * Scope approval duration times
     */
    private Integer scopeApproval;

    public ApplicationScopeSettings() {
    }

    public ApplicationScopeSettings(String scope) {
        this.scope = scope;
    }

    public ApplicationScopeSettings(ApplicationScopeSettings other) {
        this.scope = other.scope;
        this.defaultScope = other.defaultScope;
        this.parameterized = other.parameterized;
        this.scopeApproval = other.scopeApproval;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public boolean isDefaultScope() {
        return defaultScope;
    }

    public void setDefaultScope(boolean defaultScope) {
        this.defaultScope = defaultScope;
    }

    public boolean isParameterized() {
        return parameterized;
    }

    public void setParameterized(boolean parameterized) {
        this.parameterized = parameterized;
    }

    public Integer getScopeApproval() {
        return scopeApproval;
    }

    public void setScopeApproval(Integer scopeApproval) {
        this.scopeApproval = scopeApproval;
    }
}
