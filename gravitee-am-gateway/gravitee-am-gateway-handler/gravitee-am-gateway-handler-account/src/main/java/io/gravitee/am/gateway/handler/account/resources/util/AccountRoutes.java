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
package io.gravitee.am.gateway.handler.account.resources.util;

/**
 * @author Donald COURTNEY (donald.courtney at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum AccountRoutes {
    PROFILE("/api/profile"),
    ACTIVITIES("/api/activity"),
    CHANGE_PASSWORD("/api/changePassword"),
    CHANGE_PASSWORD_REDIRECT("/forgotPassword"),
    FACTORS("/api/factors"),
    FACTORS_CATALOG("/api/factors/catalog"),
    FACTORS_BY_ID("/api/factors/:factorId"),
    FACTORS_OTP_QR("/api/factors/:factorId/qr"),
    FACTORS_RECOVERY_CODE("/api/auth/recovery_code"),
    FACTORS_VERIFY("/api/factors/:factorId/verify"),
    WEBAUTHN_CREDENTIALS("/api/webauthn/credentials"),
    WEBAUTHN_CREDENTIALS_BY_ID("/api/webauthn/credentials/:credentialId"),
    CONSENT("/api/consent"),
    CONSENT_BY_ID("/api/consent/:consentId");

    private String route;

    AccountRoutes(String route){
        this.route = route;
    }

    public String getRoute() {
        return route;
    }
}
