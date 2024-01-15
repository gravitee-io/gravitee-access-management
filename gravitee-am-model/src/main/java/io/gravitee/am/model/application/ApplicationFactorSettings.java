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
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationFactorSettings {
    private String factorId;
    private String selectionRule;
    private boolean defaultFactor;

    public ApplicationFactorSettings(String factorId, String selectionRule) {
        this.factorId = factorId;
        this.selectionRule = selectionRule;
        this.defaultFactor =false;
    }

    public ApplicationFactorSettings(String factorId, String selectionRule, boolean defaultFactor) {
        this.factorId = factorId;
        this.selectionRule = selectionRule;
        this.defaultFactor = defaultFactor;
    }

    public String getFactorId() {
        return factorId;
    }

    public void setFactorId(String factorId) {
        this.factorId = factorId;
    }

    public String getSelectionRule() {
        return selectionRule;
    }

    public void setSelectionRule(String selectionRule) {
        this.selectionRule = selectionRule;
    }

    public boolean isDefaultFactor() {
        return defaultFactor;
    }

    public void setDefaultFactor(boolean defaultFactor) {
        this.defaultFactor = defaultFactor;
    }
}
