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

import io.gravitee.am.model.TokenExchangeSettings;

import java.util.List;

/**
 * MongoDB representation of TokenExchangeSettings.
 *
 * @author GraviteeSource Team
 */
public class TokenExchangeSettingsMongo {

    private boolean enabled;
    private List<String> allowedSubjectTokenTypes;
    private List<String> allowedRequestedTokenTypes;
    private boolean allowImpersonation;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedSubjectTokenTypes() {
        return allowedSubjectTokenTypes;
    }

    public void setAllowedSubjectTokenTypes(List<String> allowedSubjectTokenTypes) {
        this.allowedSubjectTokenTypes = allowedSubjectTokenTypes;
    }

    public List<String> getAllowedRequestedTokenTypes() {
        return allowedRequestedTokenTypes;
    }

    public void setAllowedRequestedTokenTypes(List<String> allowedRequestedTokenTypes) {
        this.allowedRequestedTokenTypes = allowedRequestedTokenTypes;
    }

    public boolean isAllowImpersonation() {
        return allowImpersonation;
    }

    public void setAllowImpersonation(boolean allowImpersonation) {
        this.allowImpersonation = allowImpersonation;
    }

    /**
     * Convert MongoDB representation to domain model.
     */
    public TokenExchangeSettings convert() {
        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(isEnabled());
        settings.setAllowedSubjectTokenTypes(getAllowedSubjectTokenTypes());
        settings.setAllowedRequestedTokenTypes(getAllowedRequestedTokenTypes());
        settings.setAllowImpersonation(isAllowImpersonation());
        return settings;
    }

    /**
     * Convert domain model to MongoDB representation.
     */
    public static TokenExchangeSettingsMongo convert(TokenExchangeSettings settings) {
        if (settings == null) {
            return null;
        }
        TokenExchangeSettingsMongo mongo = new TokenExchangeSettingsMongo();
        mongo.setEnabled(settings.isEnabled());
        mongo.setAllowedSubjectTokenTypes(settings.getAllowedSubjectTokenTypes());
        mongo.setAllowedRequestedTokenTypes(settings.getAllowedRequestedTokenTypes());
        mongo.setAllowImpersonation(settings.isAllowImpersonation());
        return mongo;
    }
}
