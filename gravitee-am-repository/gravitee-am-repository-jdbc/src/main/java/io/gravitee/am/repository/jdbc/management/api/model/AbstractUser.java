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
package io.gravitee.am.repository.jdbc.management.api.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class AbstractUser {
    @Id
    private String id;
    @Column("external_id")
    private String externalId;
    private String username;
    private String email;
    @Column("display_name")
    private String displayName;
    @Column("nick_name")
    private String nickName;
    @Column("first_name")
    private String firstName;
    @Column("last_name")
    private String lastName;
    private String title;
    private String type;
    @Column("preferred_language")
    private String preferredLanguage;
    @Column("account_non_expired")
    private boolean accountNonExpired = true;
    @Column("account_locked_at")
    private LocalDateTime accountLockedAt;
    @Column("account_locked_until")
    private LocalDateTime accountLockedUntil;
    @Column("account_non_locked")
    private boolean accountNonLocked = true;
    @Column("credentials_non_expired")
    private boolean credentialsNonExpired = true;
    private boolean enabled = true;
    private boolean internal;
    @Column("pre_registration")
    private boolean preRegistration;
    @Column("registration_completed")
    private boolean registrationCompleted;
    private Boolean newsletter;
    @Column("registration_user_uri")
    private String registrationUserUri;
    @Column("registration_access_token")
    private String registrationAccessToken;
    @Column("reference_type")
    private String referenceType;
    @Column("reference_id")
    private String referenceId;
    private String source;
    private String client;
    @Column("logins_count")
    private long loginsCount;
    @Column("logged_at")
    private LocalDateTime loggedAt;
    @Column("last_login_with_credentials")
    private LocalDateTime lastLoginWithCredentials;
    @Column("mfa_enrollment_skipped_at")
    private LocalDateTime mfaEnrollmentSkippedAt;
    @Column("last_password_reset")
    private LocalDateTime lastPasswordReset;
    @Column("last_logout_at")
    private LocalDateTime lastLogoutAt;
    @Column("last_username_reset")
    private LocalDateTime lastUsernameReset;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;
    // json fields
    @Column("x509_certificates")
    private String x509Certificates;
    private String factors;
    @Column("last_identity_used")
    private String lastIdentityUsed;
    @Column("additional_information")
    private String additionalInformation;
    @Column("force_reset_password")
    private Boolean forceResetPassword;
    @Column("employee_number")
    private String employeeNumber;
    @Column("cost_center")
    private String costCenter;
    @Column("organization")
    private String organization;
    @Column("division")
    private String division;
    @Column("department")
    private String department;
}
