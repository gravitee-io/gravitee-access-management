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
package io.gravitee.am.service.validators.webprotection;

import io.gravitee.am.model.webprotection.WebProtectionResolution;
import io.gravitee.am.model.webprotection.WebProtectionSettingsResolver;
import io.gravitee.am.model.webprotection.XFrameSettings;
import io.gravitee.am.service.exception.InvalidDomainException;
import io.reactivex.rxjava3.core.Completable;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * @author GraviteeSource Team
 */
@Component
public class XFrameSettingsValidatorImpl implements XFrameSettingsValidator {

    @Override
    public Completable validate(XFrameSettings settings) {
        if (WebProtectionSettingsResolver.resolve(settings) != WebProtectionResolution.ENABLED) {
            return Completable.complete();
        }

        final String action = settings.getAction();
        if (action == null || action.isBlank()) {
            return Completable.complete();
        }

        if (!XFrameSettings.ALLOWED_ACTIONS.contains(action.trim().toUpperCase(Locale.ROOT))) {
            return Completable.error(new InvalidDomainException(
                    "X-Frame-Options action '%s' is not supported, supported values are %s"
                            .formatted(action, String.join(", ", XFrameSettings.ALLOWED_ACTIONS))));
        }

        return Completable.complete();
    }
}
