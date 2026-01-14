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
package io.gravitee.am.service.model.openid;

import io.gravitee.am.model.oidc.TokenExchangeSettings;
import io.gravitee.am.service.utils.SetterUtils;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * @author GraviteeSource Team
 */
@NoArgsConstructor
public class PatchTokenExchangeSettings {

    private Optional<Boolean> enabled;
    private Optional<Boolean> allowImpersonation;
    private Optional<Boolean> allowDelegation;
    private Optional<Boolean> requireClientAuthentication;
    private Optional<Boolean> allowScopeDownscoping;
    private Optional<Double> tokenLifetimeMultiplier;

    public Optional<Boolean> getEnabled() {
        return enabled;
    }

    public void setEnabled(Optional<Boolean> enabled) {
        this.enabled = enabled;
    }

    public Optional<Boolean> getAllowImpersonation() {
        return allowImpersonation;
    }

    public void setAllowImpersonation(Optional<Boolean> allowImpersonation) {
        this.allowImpersonation = allowImpersonation;
    }

    public Optional<Boolean> getAllowDelegation() {
        return allowDelegation;
    }

    public void setAllowDelegation(Optional<Boolean> allowDelegation) {
        this.allowDelegation = allowDelegation;
    }

    public Optional<Boolean> getRequireClientAuthentication() {
        return requireClientAuthentication;
    }

    public void setRequireClientAuthentication(Optional<Boolean> requireClientAuthentication) {
        this.requireClientAuthentication = requireClientAuthentication;
    }

    public Optional<Boolean> getAllowScopeDownscoping() {
        return allowScopeDownscoping;
    }

    public void setAllowScopeDownscoping(Optional<Boolean> allowScopeDownscoping) {
        this.allowScopeDownscoping = allowScopeDownscoping;
    }

    public Optional<Double> getTokenLifetimeMultiplier() {
        return tokenLifetimeMultiplier;
    }

    public void setTokenLifetimeMultiplier(Optional<Double> tokenLifetimeMultiplier) {
        this.tokenLifetimeMultiplier = tokenLifetimeMultiplier;
    }

    public TokenExchangeSettings patch(TokenExchangeSettings toPatch) {
        TokenExchangeSettings result = toPatch != null ? toPatch : TokenExchangeSettings.defaultSettings();

        SetterUtils.safeSet(result::setEnabled, this.getEnabled(), boolean.class);
        SetterUtils.safeSet(result::setAllowImpersonation, this.getAllowImpersonation(), boolean.class);
        SetterUtils.safeSet(result::setAllowDelegation, this.getAllowDelegation(), boolean.class);
        SetterUtils.safeSet(result::setRequireClientAuthentication, this.getRequireClientAuthentication(), boolean.class);
        SetterUtils.safeSet(result::setAllowScopeDownscoping, this.getAllowScopeDownscoping(), boolean.class);
        SetterUtils.safeSet(result::setTokenLifetimeMultiplier, this.getTokenLifetimeMultiplier(), double.class);

        return result;
    }
}
