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
import io.gravitee.am.model.application.TokenExchangeOAuthSettings;

import java.util.List;
import java.util.Objects;

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
    private List<String> allowedActorTokenTypes;
    private boolean allowDelegation;
    private Integer maxDelegationDepth;
    private List<TrustedIssuerMongo> trustedIssuers;
    private TokenExchangeOAuthSettingsMongo tokenExchangeOAuthSettings;

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

    public List<String> getAllowedActorTokenTypes() {
        return allowedActorTokenTypes;
    }

    public void setAllowedActorTokenTypes(List<String> allowedActorTokenTypes) {
        this.allowedActorTokenTypes = allowedActorTokenTypes;
    }

    public boolean isAllowDelegation() {
        return allowDelegation;
    }

    public void setAllowDelegation(boolean allowDelegation) {
        this.allowDelegation = allowDelegation;
    }

    public Integer getMaxDelegationDepth() {
        return maxDelegationDepth;
    }

    public void setMaxDelegationDepth(Integer maxDelegationDepth) {
        this.maxDelegationDepth = maxDelegationDepth;
    }

    public List<TrustedIssuerMongo> getTrustedIssuers() {
        return trustedIssuers;
    }

    public void setTrustedIssuers(List<TrustedIssuerMongo> trustedIssuers) {
        this.trustedIssuers = trustedIssuers;
    }

    public TokenExchangeOAuthSettingsMongo getTokenExchangeOAuthSettings() {
        return tokenExchangeOAuthSettings;
    }

    public void setTokenExchangeOAuthSettings(TokenExchangeOAuthSettingsMongo tokenExchangeOAuthSettings) {
        this.tokenExchangeOAuthSettings = tokenExchangeOAuthSettings;
    }

    /**
     * Convert MongoDB representation to domain model.
     * When maxDelegationDepth is null (old data), the domain model default applies.
     */
    public TokenExchangeSettings convert() {
        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(isEnabled());
        settings.setAllowedSubjectTokenTypes(getAllowedSubjectTokenTypes());
        settings.setAllowedRequestedTokenTypes(getAllowedRequestedTokenTypes());
        settings.setAllowImpersonation(isAllowImpersonation());
        settings.setAllowedActorTokenTypes(getAllowedActorTokenTypes());
        settings.setAllowDelegation(isAllowDelegation());
        if (maxDelegationDepth != null) {
            settings.setMaxDelegationDepth(maxDelegationDepth);
        }
        if (trustedIssuers != null) {
            settings.setTrustedIssuers(trustedIssuers.stream()
                    .map(TrustedIssuerMongo::convert)
                    .filter(Objects::nonNull)
                    .toList());
        }
        if (tokenExchangeOAuthSettings != null) {
            settings.setTokenExchangeOAuthSettings(tokenExchangeOAuthSettings.convert());
        }
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
        mongo.setAllowedActorTokenTypes(settings.getAllowedActorTokenTypes());
        mongo.setAllowDelegation(settings.isAllowDelegation());
        mongo.setMaxDelegationDepth(settings.getMaxDelegationDepth());
        if (settings.getTrustedIssuers() != null) {
            mongo.setTrustedIssuers(settings.getTrustedIssuers().stream()
                    .map(TrustedIssuerMongo::convert)
                    .toList());
        }
        mongo.setTokenExchangeOAuthSettings(TokenExchangeOAuthSettingsMongo.convert(settings.getTokenExchangeOAuthSettings()));
        return mongo;
    }
}
