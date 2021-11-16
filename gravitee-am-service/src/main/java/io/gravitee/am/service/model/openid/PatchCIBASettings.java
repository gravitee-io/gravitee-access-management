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

import io.gravitee.am.model.oidc.CIBASettings;
import io.gravitee.am.model.oidc.CIBASettingNotifier;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.List;
import java.util.Optional;

/**
 * @@author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchCIBASettings {

    public PatchCIBASettings() {}

    /**
     * true if CIBA flow is enabled for the domain
     */
    private Optional<Boolean> enabled;
    /**
     * validity (in sec) of the auth_req_id
     */
    private Optional<Integer> authReqExpiry;
    /**
     * Delay between two calls on the token endpoint using the same auth_req_id
     */
    private Optional<Integer> tokenReqInterval;
    /**
     * MaxLength of the binding_message parameter
     */
    private Optional<Integer> bindingMessageLength;
    /**
     * Authentication Device Notifiers to use when the EndUser has to be notified
     */
    private Optional<List<CIBASettingNotifier>> deviceNotifiers;

    public Optional<Boolean> getEnabled() {
        return enabled;
    }

    public void setEnabled(Optional<Boolean> enabled) {
        this.enabled = enabled;
    }

    public Optional<Integer> getAuthReqExpiry() {
        return authReqExpiry;
    }

    public void setAuthReqExpiry(Optional<Integer> authReqExpiry) {
        this.authReqExpiry = authReqExpiry;
    }

    public Optional<Integer> getTokenReqInterval() {
        return tokenReqInterval;
    }

    public void setTokenReqInterval(Optional<Integer> tokenReqInterval) {
        this.tokenReqInterval = tokenReqInterval;
    }

    public Optional<Integer> getBindingMessageLength() {
        return bindingMessageLength;
    }

    public void setBindingMessageLength(Optional<Integer> bindingMessageLength) {
        this.bindingMessageLength = bindingMessageLength;
    }

    public Optional<List<CIBASettingNotifier>> getDeviceNotifiers() {
        return deviceNotifiers;
    }

    public void setDeviceNotifiers(Optional<List<CIBASettingNotifier>> deviceNotifiers) {
        this.deviceNotifiers = deviceNotifiers;
    }

    public CIBASettings patch(CIBASettings toPatch) {
        CIBASettings result=toPatch!=null ? toPatch : CIBASettings.defaultSettings();

        SetterUtils.safeSet(result::setEnabled, this.getEnabled(), boolean.class);
        SetterUtils.safeSet(result::setAuthReqExpiry, this.getAuthReqExpiry(), int.class);
        SetterUtils.safeSet(result::setTokenReqInterval, this.getTokenReqInterval(), int.class);
        SetterUtils.safeSet(result::setBindingMessageLength, this.getBindingMessageLength(), int.class);
        SetterUtils.safeSet(result::setDeviceNotifiers, this.getDeviceNotifiers(), List.class);

        return result;
    }
}
