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
package io.gravitee.am.model.oidc;

/**
 * Token Exchange Settings for RFC 8693 OAuth 2.0 Token Exchange
 *
 * @author GraviteeSource Team
 */
public class TokenExchangeSettings {

    /**
     * Enable token exchange at domain level.
     * When disabled, all token exchange requests will be rejected regardless of application settings.
     */
    private boolean enabled = false;

    /**
     * Allow impersonation mode where the acting party becomes indistinguishable from the subject.
     * When false, only delegation mode (with 'act' claim) is allowed.
     * Security consideration: Impersonation is more powerful and should be used with caution.
     */
    private boolean allowImpersonation = false;

    /**
     * Allow delegation mode where the 'act' claim is added to track the acting party.
     */
    private boolean allowDelegation = true;

    /**
     * Require client authentication for token exchange requests.
     * Strongly recommended to be enabled for security.
     */
    private boolean requireClientAuthentication = true;

    /**
     * Allow downscoping of tokens during exchange.
     * When enabled, the exchanged token can have a subset of the original token's scopes.
     */
    private boolean allowScopeDownscoping = true;

    /**
     * Token lifetime multiplier for exchanged tokens (relative to the original token's remaining lifetime).
     * Value between 0 and 1. For example, 0.5 means the exchanged token will have at most half
     * the remaining lifetime of the subject token.
     * A value of 0 means use the default token lifetime settings.
     */
    private double tokenLifetimeMultiplier = 0.0;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAllowImpersonation() {
        return allowImpersonation;
    }

    public void setAllowImpersonation(boolean allowImpersonation) {
        this.allowImpersonation = allowImpersonation;
    }

    public boolean isAllowDelegation() {
        return allowDelegation;
    }

    public void setAllowDelegation(boolean allowDelegation) {
        this.allowDelegation = allowDelegation;
    }

    public boolean isRequireClientAuthentication() {
        return requireClientAuthentication;
    }

    public void setRequireClientAuthentication(boolean requireClientAuthentication) {
        this.requireClientAuthentication = requireClientAuthentication;
    }

    public boolean isAllowScopeDownscoping() {
        return allowScopeDownscoping;
    }

    public void setAllowScopeDownscoping(boolean allowScopeDownscoping) {
        this.allowScopeDownscoping = allowScopeDownscoping;
    }

    public double getTokenLifetimeMultiplier() {
        return tokenLifetimeMultiplier;
    }

    public void setTokenLifetimeMultiplier(double tokenLifetimeMultiplier) {
        this.tokenLifetimeMultiplier = tokenLifetimeMultiplier;
    }

    /**
     * Create default settings with secure defaults.
     * Token exchange is disabled by default and requires explicit opt-in.
     */
    public static TokenExchangeSettings defaultSettings() {
        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(false);
        settings.setAllowImpersonation(false);
        settings.setAllowDelegation(true);
        settings.setRequireClientAuthentication(true);
        settings.setAllowScopeDownscoping(true);
        settings.setTokenLifetimeMultiplier(0.0);
        return settings;
    }
}
