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
package io.gravitee.am.gateway.handler.common.webauthn;

import io.gravitee.am.model.User;
import io.gravitee.am.model.login.LoginSettings;

import java.util.Date;

import static io.gravitee.am.common.utils.ConstantKeys.DEFAULT_WEBAUTHN_REGISTRATION_SKIP_TIME_SECONDS;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class WebAuthnRegistrationSkipUtils {

    private static final long SECONDS_IN_HOUR = 3600L;
    private static final long SECONDS_IN_DAY = 24L * SECONDS_IN_HOUR;
    private static final long SECONDS_IN_WEEK = 7L * SECONDS_IN_DAY;
    private static final long SECONDS_IN_MONTH = 30L * SECONDS_IN_DAY;

    private WebAuthnRegistrationSkipUtils() {
    }

    public record SkipDurationDisplay(long value, String unitKey) {
    }

    public static long getSkipTimeSeconds(LoginSettings loginSettings) {
        return ofNullable(loginSettings)
                .map(LoginSettings::getPasswordlessRegistrationSkipTimeSeconds)
                .orElse(DEFAULT_WEBAUTHN_REGISTRATION_SKIP_TIME_SECONDS);
    }

    public static boolean isRegistrationSkipped(User user, LoginSettings loginSettings) {
        if (user == null || user.getWebAuthnRegistrationSkippedAt() == null) {
            return false;
        }
        long skipTimeMs = getSkipTimeSeconds(loginSettings) * 1000L;
        return user.getWebAuthnRegistrationSkippedAt().getTime() + skipTimeMs > new Date().getTime();
    }

    public static SkipDurationDisplay toDisplay(long skipTimeSeconds) {
        if (skipTimeSeconds >= SECONDS_IN_MONTH && skipTimeSeconds % SECONDS_IN_MONTH == 0) {
            return new SkipDurationDisplay(skipTimeSeconds / SECONDS_IN_MONTH, "months");
        }
        if (skipTimeSeconds >= SECONDS_IN_WEEK && skipTimeSeconds % SECONDS_IN_WEEK == 0) {
            return new SkipDurationDisplay(skipTimeSeconds / SECONDS_IN_WEEK, "weeks");
        }
        if (skipTimeSeconds >= SECONDS_IN_DAY && skipTimeSeconds % SECONDS_IN_DAY == 0) {
            return new SkipDurationDisplay(skipTimeSeconds / SECONDS_IN_DAY, "days");
        }
        if (skipTimeSeconds >= SECONDS_IN_HOUR && skipTimeSeconds % SECONDS_IN_HOUR == 0) {
            return new SkipDurationDisplay(skipTimeSeconds / SECONDS_IN_HOUR, "hours");
        }
        return new SkipDurationDisplay(Math.max(1, (skipTimeSeconds + SECONDS_IN_DAY - 1) / SECONDS_IN_DAY), "days");
    }
}
