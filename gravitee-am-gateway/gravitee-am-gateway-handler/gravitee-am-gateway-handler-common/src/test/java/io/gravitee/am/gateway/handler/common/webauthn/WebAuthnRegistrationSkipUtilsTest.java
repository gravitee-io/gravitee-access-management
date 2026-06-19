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
import org.junit.jupiter.api.Test;

import java.util.Date;

import static io.gravitee.am.common.utils.ConstantKeys.DEFAULT_WEBAUTHN_REGISTRATION_SKIP_TIME_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
class WebAuthnRegistrationSkipUtilsTest {

    @Test
    void shouldUseDefaultSkipTimeWhenNotConfigured() {
        assertEquals(DEFAULT_WEBAUTHN_REGISTRATION_SKIP_TIME_SECONDS, WebAuthnRegistrationSkipUtils.getSkipTimeSeconds(null));
        assertEquals(DEFAULT_WEBAUTHN_REGISTRATION_SKIP_TIME_SECONDS, WebAuthnRegistrationSkipUtils.getSkipTimeSeconds(new LoginSettings()));
    }

    @Test
    void shouldUseConfiguredSkipTime() {
        LoginSettings loginSettings = new LoginSettings();
        loginSettings.setPasswordlessRegistrationSkipTimeSeconds(604800L);

        assertEquals(604800L, WebAuthnRegistrationSkipUtils.getSkipTimeSeconds(loginSettings));
    }

    @Test
    void shouldDetectActiveSkipPeriod() {
        LoginSettings loginSettings = new LoginSettings();
        loginSettings.setPasswordlessRegistrationSkipTimeSeconds(3600L);

        User user = new User();
        user.setWebAuthnRegistrationSkippedAt(new Date());

        assertTrue(WebAuthnRegistrationSkipUtils.isRegistrationSkipped(user, loginSettings));
    }

    @Test
    void shouldDetectExpiredSkipPeriod() {
        LoginSettings loginSettings = new LoginSettings();
        loginSettings.setPasswordlessRegistrationSkipTimeSeconds(3600L);

        User user = new User();
        user.setWebAuthnRegistrationSkippedAt(new Date(System.currentTimeMillis() - 7200_000L));

        assertFalse(WebAuthnRegistrationSkipUtils.isRegistrationSkipped(user, loginSettings));
    }

    @Test
    void shouldFormatSkipDurationForDisplay() {
        assertEquals("days", WebAuthnRegistrationSkipUtils.toDisplay(86400L).unitKey());
        assertEquals(1, WebAuthnRegistrationSkipUtils.toDisplay(86400L).value());
        assertEquals("weeks", WebAuthnRegistrationSkipUtils.toDisplay(604800L).unitKey());
        assertEquals(1, WebAuthnRegistrationSkipUtils.toDisplay(604800L).value());
        assertEquals("months", WebAuthnRegistrationSkipUtils.toDisplay(2592000L).unitKey());
        assertEquals(1, WebAuthnRegistrationSkipUtils.toDisplay(2592000L).value());
    }
}
