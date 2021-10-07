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
package io.gravitee.am.model.scim;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SCIMSettings {

    /**
     * Enable/disable SCIM feature
     */
    private boolean enabled;

    /**
     * Enable/disable IdP selection feature
     */
    private boolean idpSelectionEnabled;

    /**
     * Identity provider selection rule
     */
    private String idpSelectionRule;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isIdpSelectionEnabled() {
        return idpSelectionEnabled;
    }

    public void setIdpSelectionEnabled(boolean idpSelectionEnabled) {
        this.idpSelectionEnabled = idpSelectionEnabled;
    }

    public String getIdpSelectionRule() {
        return idpSelectionRule;
    }

    public void setIdpSelectionRule(String idpSelectionRule) {
        this.idpSelectionRule = idpSelectionRule;
    }
}
