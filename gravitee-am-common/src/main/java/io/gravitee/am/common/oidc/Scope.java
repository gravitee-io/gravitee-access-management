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

    PROFILE("profile","Profile","Access to the End-User default profile Claims", Arrays.asList(
            StandardClaims.NAME, StandardClaims.FAMILY_NAME, StandardClaims.GIVEN_NAME, StandardClaims.MIDDLE_NAME,
            StandardClaims.NICKNAME, StandardClaims.PREFERRED_USERNAME, StandardClaims.PROFILE, StandardClaims.PICTURE,
            StandardClaims.WEBSITE, StandardClaims.GENDER, StandardClaims.BIRTHDATE, StandardClaims.ZONEINFO, StandardClaims.LOCALE,
            StandardClaims.UPDATED_AT),true),
    FULL_PROFILE("full_profile", "Extended profile", "Access to the End-User extended profile Claims", Collections.emptyList(), true),
    EMAIL("email","Email","Access to the email and email_verified Claims", Arrays.asList(StandardClaims.EMAIL, StandardClaims.EMAIL_VERIFIED),true),
    ADDRESS("address", "Address","Access to the address Claim", Arrays.asList(StandardClaims.ADDRESS),true),
    PHONE("phone", "Phone", "Access to the phone_number and phone_number_verified Claims", Arrays.asList(StandardClaims.PHONE_NUMBER, StandardClaims.PHONE_NUMBER_VERIFIED),true),
    OPENID("openid","Openid","Used to perform Openid requests", Collections.emptyList(),true),
    OFFLINE_ACCESS("offline_access", "Offline_access","Access to End-User UserInfo even when he is not logged in.", Collections.emptyList(),true),
    DCR("dcr", "Client_registration", "Access to client information through openid register endpoint.", Collections.emptyList(),false),
    DCR_ADMIN("dcr_admin", "Client_registration_admin", "Access to Dynamic Client Registration endpoint.", Collections.emptyList(),false),
    SCIM("scim", "SCIM", "Access to System for Cross-domain Identity Management endpoint.", Collections.emptyList(),false),
    UMA("uma_protection", "UMA_2.0","Access and manage user protected resources", Collections.emptyList(), false),
    CONSENT_ADMIN("consent_admin", "Consent_admin", "Access to End-User consents", Collections.emptyList(),false),
    GROUPS("groups", "Groups", "Access to the groups Claim.", Arrays.asList(CustomClaims.GROUPS), true),
    ROLES("roles", "Roles", "Access to the roles Claim.", Arrays.asList(CustomClaims.ROLES), true);

    private final String key;
    private final String label;
    private final String description;
    private final List<String> claims;
    private final boolean discovery;

    //See https://tools.ietf.org/html/rfc6749#section-3.3
    public final static String SCOPE_DELIMITER = " ";

    Scope(String key, String label, String description, List<String> claims, boolean discovery) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.claims = claims;
        this.discovery = discovery;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getClaims() {
        return claims;
    }

    public static boolean exists(String scope) {
        try {
            Scope.valueOf(scope);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isDiscovery() {
        return discovery;
    }
}
