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
package io.gravitee.am.model;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFASettings {

    private String loginRule;
    private String stepUpAuthenticationRule;
    private String adaptiveAuthenticationRule;

    public MFASettings() {
    }

    public MFASettings(MFASettings other) {
        this.loginRule = other.loginRule;
        this.stepUpAuthenticationRule = other.stepUpAuthenticationRule;
        this.adaptiveAuthenticationRule = other.adaptiveAuthenticationRule;
    }

    public String getLoginRule() {
        return loginRule;
    }

    public void setLoginRule(String loginRule) {
        this.loginRule = loginRule;
    }

    public String getStepUpAuthenticationRule() {
        return stepUpAuthenticationRule;
    }

    public void setStepUpAuthenticationRule(String stepUpAuthenticationRule) {
        this.stepUpAuthenticationRule = stepUpAuthenticationRule;
    }

    public String getAdaptiveAuthenticationRule() {
        return adaptiveAuthenticationRule;
    }

    public void setAdaptiveAuthenticationRule(String adaptiveAuthenticationRule) {
        this.adaptiveAuthenticationRule = adaptiveAuthenticationRule;
    }
}
