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
package io.gravitee.am.common.oidc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * OpenID Connect Clients use scope values, as defined in Section 3.3 of OAuth 2.0 [RFC6749], to specify what access privileges are being requested for Access Tokens.
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims">5.4. Requesting Claims using Scope Values</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum Scope {

    PROFILE("profile", Arrays.asList(
            StandardClaims.NAME, StandardClaims.FAMILY_NAME, StandardClaims.GIVEN_NAME, StandardClaims.MIDDLE_NAME,
            StandardClaims.NICKNAME, StandardClaims.PREFERRED_USERNAME, StandardClaims.PROFILE, StandardClaims.PICTURE,
            StandardClaims.WEBSITE, StandardClaims.GENDER, StandardClaims.BIRTHDATE, StandardClaims.ZONEINFO, StandardClaims.LOCALE,
            StandardClaims.UPDATED_AT)),
    EMAIL("email", Arrays.asList(StandardClaims.EMAIL, StandardClaims.EMAIL_VERIFIED)),
    ADDRESS("address", Arrays.asList(StandardClaims.ADDRESS)),
    PHONE("phone", Arrays.asList(StandardClaims.PHONE_NUMBER, StandardClaims.PHONE_NUMBER_VERIFIED)),
    OPENID("openid", Collections.emptyList()),
    OFFLINE_ACCESS("offline_access", Collections.emptyList());

    private final String name;
    private final List<String> claims;

    Scope(String name, List<String> claims) {
        this.name = name;
        this.claims = claims;
    }

    public String getName() {
        return name;
    }

    public List<String> getClaims() {
        return claims;
    }
}
