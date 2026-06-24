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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.gravitee.am.model.CorsSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.webprotection.CspSettings;
import io.gravitee.am.model.webprotection.WebProtectionResolution;
import io.gravitee.am.model.webprotection.WebProtectionSettings;
import io.gravitee.am.model.webprotection.WebProtectionSettingsResolver;
import io.gravitee.am.model.webprotection.XFrameSettings;
import io.gravitee.am.model.webprotection.XssProtectionSettings;

import static java.util.Optional.ofNullable;

/**
 * Resolves domain-level web protection overrides.
 */
public final class WebProtectionDomainSettings {

    private WebProtectionDomainSettings() {
    }

    public static WebProtectionResolution corsResolution(Domain domain) {
        return WebProtectionSettingsResolver.resolve(domain.getCorsSettings());
    }

    public static WebProtectionResolution cspResolution(Domain domain) {
        return WebProtectionSettingsResolver.resolve(ofNullable(domain.getWebProtectionSettings())
                .map(WebProtectionSettings::getCsp)
                .orElse(null));
    }

    public static WebProtectionResolution xframeResolution(Domain domain) {
        return WebProtectionSettingsResolver.resolve(ofNullable(domain.getWebProtectionSettings())
                .map(WebProtectionSettings::getXframe)
                .orElse(null));
    }

    public static WebProtectionResolution xssResolution(Domain domain) {
        return WebProtectionSettingsResolver.resolve(ofNullable(domain.getWebProtectionSettings())
                .map(WebProtectionSettings::getXss)
                .orElse(null));
    }

    public static CspSettings csp(Domain domain) {
        return ofNullable(domain.getWebProtectionSettings())
                .map(WebProtectionSettings::getCsp)
                .orElse(null);
    }

    public static XFrameSettings xframe(Domain domain) {
        return ofNullable(domain.getWebProtectionSettings())
                .map(WebProtectionSettings::getXframe)
                .orElse(null);
    }

    public static XssProtectionSettings xss(Domain domain) {
        return ofNullable(domain.getWebProtectionSettings())
                .map(WebProtectionSettings::getXss)
                .orElse(null);
    }

    public static CorsSettings cors(Domain domain) {
        return domain.getCorsSettings();
    }
}
