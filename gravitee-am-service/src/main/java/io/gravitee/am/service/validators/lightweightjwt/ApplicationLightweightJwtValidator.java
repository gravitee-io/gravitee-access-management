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
package io.gravitee.am.service.validators.lightweightjwt;

import io.gravitee.am.model.application.ApplicationLightweightJwtSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.service.validators.Validator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * @author GraviteeSource Team
 */
@Component
public class ApplicationLightweightJwtValidator implements Validator<ApplicationOAuthSettings, Optional<String>> {

    public static final int CLAIM_NAME_MAX_LENGTH = 128;

    @Override
    public Optional<String> validate(ApplicationOAuthSettings oauthSettings) {
        if (oauthSettings == null || oauthSettings.getLightweightJwtSettings() == null) {
            return Optional.empty();
        }
        ApplicationLightweightJwtSettings settings = oauthSettings.getLightweightJwtSettings();
        return validateAllowlist("accessTokenAllowlist", settings.getAccessTokenAllowlist())
                .or(() -> validateAllowlist("idTokenAllowlist", settings.getIdTokenAllowlist()));
    }

    private Optional<String> validateAllowlist(String name, List<String> allowlist) {
        if (allowlist == null) {
            return Optional.empty();
        }
        for (String claim : allowlist) {
            if (claim == null || claim.isBlank()) {
                return Optional.of("lightweightJwtSettings." + name + " must not contain a blank claim name");
            }
            if (claim.length() > CLAIM_NAME_MAX_LENGTH) {
                return Optional.of("lightweightJwtSettings." + name + " claim names must be at most " + CLAIM_NAME_MAX_LENGTH + " characters");
            }
        }
        return Optional.empty();
    }
}
