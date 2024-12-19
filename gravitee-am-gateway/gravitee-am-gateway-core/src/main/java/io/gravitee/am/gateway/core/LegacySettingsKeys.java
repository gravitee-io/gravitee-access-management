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

package io.gravitee.am.gateway.core;


import org.springframework.core.env.Environment;

import static java.util.Objects.isNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum LegacySettingsKeys {

    HANDLER_SKIP_BYPASS_DIRECT_REQUEST_HDL("legacy.handler.skipBypassDirectRequestHandler", Boolean.FALSE),
    OIDC_FILTER_CUSTOM_PROMPT("legacy.openid.filterCustomPrompt", Boolean.FALSE),
    OIDC_SCOPE_FULL_PROFILE("legacy.openid.openid_scope_full_profile", Boolean.FALSE),
    OIDC_ALWAYS_ENHANCE_SCOPE("legacy.openid.always_enhance_scopes", Boolean.FALSE),
    HANDLER_ALWAYS_APPLY_BODY_HDL("legacy.handler.alwaysApplyBodyHandler", Boolean.FALSE),
    REGISTRATION_KEEP_PARAMS("legacy.registration.keepParams", Boolean.TRUE),
    RESET_PWD_KEEP_PARAMS("legacy.resetPassword.keepParam", Boolean.TRUE),
    OIDC_SANITIZE_PARAM_ENCODING("legacy.openid.sanitizeParametersEncoding", Boolean.TRUE);

    private String key;
    private Boolean defaultValue;

    LegacySettingsKeys(String key, Boolean defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return key;
    }

    public Boolean getDefaultValue() {
        return defaultValue;
    }

    public boolean from(Environment environment) {
        return isNull(environment) ? defaultValue : environment.getProperty(key, Boolean.class, defaultValue);
    }
}
