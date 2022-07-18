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
package io.gravitee.am.service.utils;

import io.gravitee.am.common.oidc.ClaimType;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.model.User;
import org.junit.Test;

import java.util.Locale;

import static io.gravitee.am.service.utils.UserProfileUtils.preferredLanguage;
import static org.junit.Assert.assertEquals;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserProfileUtilsTest {

    @Test
    public void shouldReturnDefaultLanguage_MissingUser() {
        assertEquals(Locale.CANADA, preferredLanguage(null, Locale.CANADA));
    }

    @Test
    public void shouldReturnDefaultLanguage_NoPreferredLanguage() {
        assertEquals(Locale.CANADA, preferredLanguage(new User(), Locale.CANADA));
    }

    @Test
    public void shouldReturnPreferredLanguage() {
        final User user = new User();
        user.setPreferredLanguage("en");
        assertEquals(Locale.ENGLISH, preferredLanguage(user, Locale.CANADA));
    }

    @Test
    public void shouldReturnPreferredLanguage_CountryCode_dash() {
        final User user = new User();
        user.setPreferredLanguage("en-GB");
        assertEquals(new Locale("en", "GB"), preferredLanguage(user, Locale.CANADA));
    }

    @Test
    public void shouldReturnPreferredLanguage_CountryCode_underscore() {
        final User user = new User();
        user.setPreferredLanguage("en_GB");
        assertEquals(new Locale("en", "GB"), preferredLanguage(user, Locale.CANADA));
    }

    @Test
    public void shouldReturnLocaleClaim() {
        final User user = new User();
        user.putAdditionalInformation(StandardClaims.LOCALE, "en");
        assertEquals(Locale.ENGLISH, preferredLanguage(user, Locale.CANADA));
    }

    @Test
    public void shouldReturnLocaleClaim_CountryCode_dash() {
        final User user = new User();
        user.putAdditionalInformation(StandardClaims.LOCALE, "en-GB");
        assertEquals(new Locale("en", "GB"), preferredLanguage(user, Locale.CANADA));
    }

    @Test
    public void shouldReturnLocaleClaim_CountryCode_underscore() {
        final User user = new User();
        user.putAdditionalInformation(StandardClaims.LOCALE, "en_GB");
        assertEquals(new Locale("en", "GB"), preferredLanguage(user, Locale.CANADA));
    }

}
