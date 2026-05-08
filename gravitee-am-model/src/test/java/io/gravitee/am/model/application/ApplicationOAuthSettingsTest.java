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
package io.gravitee.am.model.application;

import io.gravitee.am.model.UserInfoClaim;
import io.gravitee.am.model.oidc.Client;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

/**
 * @author GraviteeSource Team
 */
public class ApplicationOAuthSettingsTest {

    @Test
    public void copyConstructor_copies_userinfoCustomClaims_independently() {
        ApplicationOAuthSettings source = new ApplicationOAuthSettings();
        List<UserInfoClaim> claims = new ArrayList<>();
        claims.add(UserInfoClaim.of("a", "#a"));
        source.setUserinfoCustomClaims(claims);

        ApplicationOAuthSettings copy = new ApplicationOAuthSettings(source);

        assertEquals(1, copy.getUserinfoCustomClaims().size());
        assertEquals("a", copy.getUserinfoCustomClaims().get(0).getClaimName());
        assertNotSame(source.getUserinfoCustomClaims(), copy.getUserinfoCustomClaims());

        // mutating source must not affect copy
        source.getUserinfoCustomClaims().add(UserInfoClaim.of("b", "#b"));
        assertEquals(1, copy.getUserinfoCustomClaims().size());
    }

    @Test
    public void copyConstructor_handles_null_userinfoCustomClaims() {
        ApplicationOAuthSettings source = new ApplicationOAuthSettings();
        source.setUserinfoCustomClaims(null);

        ApplicationOAuthSettings copy = new ApplicationOAuthSettings(source);

        assertNull(copy.getUserinfoCustomClaims());
    }

    @Test
    public void copyTo_propagates_userinfoCustomClaims_to_client() {
        ApplicationOAuthSettings settings = new ApplicationOAuthSettings();
        List<UserInfoClaim> claims = List.of(UserInfoClaim.of("custom", "#expr"));
        settings.setUserinfoCustomClaims(claims);

        Client client = new Client();
        settings.copyTo(client);

        assertEquals(claims, client.getUserinfoCustomClaims());
    }
}
