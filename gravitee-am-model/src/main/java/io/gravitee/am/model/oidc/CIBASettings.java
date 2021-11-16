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

import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CIBASettings {
    public static final int DEFAULT_EXPIRY_IN_SEC = 600;
    public static final int DEFAULT_INTERVAL_IN_SEC = 5;
    public static final int DEFAULT_MSG_LENGTH = 256;

    private boolean enabled;
    /**
     * validity (in sec) of the auth_req_id
     */
    private int authReqExpiry = DEFAULT_EXPIRY_IN_SEC;
    /**
     * Delay between two calls on the token endpoint using the same auth_req_id
     */
    private int tokenReqInterval = DEFAULT_INTERVAL_IN_SEC;
    /**
     * MaxLength of the binding_message parameter
     */
    private int bindingMessageLength = DEFAULT_MSG_LENGTH;
    /**
     * Authentication Device Notifiers to use when the EndUser has to be notified
     */
    private List<CIBASettingNotifier> deviceNotifiers;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAuthReqExpiry() {
        return authReqExpiry;
    }

    public void setAuthReqExpiry(int authReqExpiry) {
        this.authReqExpiry = authReqExpiry;
    }

    public int getTokenReqInterval() {
        return tokenReqInterval;
    }

    public void setTokenReqInterval(int tokenReqInterval) {
        this.tokenReqInterval = tokenReqInterval;
    }

    public int getBindingMessageLength() {
        return bindingMessageLength;
    }

    public void setBindingMessageLength(int bindingMessageLength) {
        this.bindingMessageLength = bindingMessageLength;
    }

    public static CIBASettings defaultSettings() {
        return new CIBASettings();
    }

    public List<CIBASettingNotifier> getDeviceNotifiers() {
        return deviceNotifiers;
    }

    public void setDeviceNotifiers(List<CIBASettingNotifier> deviceNotifiers) {
        this.deviceNotifiers = deviceNotifiers;
    }
}
