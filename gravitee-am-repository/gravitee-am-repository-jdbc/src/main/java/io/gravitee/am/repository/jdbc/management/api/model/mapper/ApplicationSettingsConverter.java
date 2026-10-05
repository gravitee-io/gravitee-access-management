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
package io.gravitee.am.repository.jdbc.management.api.model.mapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.github.dozermapper.core.DozerConverter;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.repository.jdbc.provider.common.JSONMapper;
import lombok.CustomLog;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class ApplicationSettingsConverter extends DozerConverter<ApplicationSettings, String> {

    public ApplicationSettingsConverter() {
        super(ApplicationSettings.class, String.class);
    }

    @Override
    public String convertTo(ApplicationSettings bean, String s) {
        return JSONMapper.toJson(bean);
    }

    @Override
    public ApplicationSettings convertFrom(String s, ApplicationSettings bean) {
        ApplicationSettings settings = JSONMapper.toBean(s, ApplicationSettings.class, DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
        if (settings != null && settings.getOauth() != null && settings.getOauth().getTokenCustomClaims() != null) {
            settings.getOauth().getTokenCustomClaims().removeIf(this::hasUnknownTokenType);
        }
        return settings;
    }

    private boolean hasUnknownTokenType(TokenClaim claim) {
        if (claim.getTokenType() != null) {
            return false;
        }
        log.warn("Ignoring token custom claim with unknown token type claimName={}", claim.getClaimName());
        return true;
    }
}
