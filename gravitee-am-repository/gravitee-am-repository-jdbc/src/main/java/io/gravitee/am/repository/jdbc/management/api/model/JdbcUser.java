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

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Table("users")
public class JdbcUser {
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
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;
    // json fields
    @Column("x509_certificates")
    private String x509Certificates;
    private String factors;
    @Column("additional_information")
    private String additionalInformation;
    /**
     * Copy of additionalInformation.email to allow efficient search
     */
    @Column("additional_information_email")
    private String additionalInformationEmail;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAdditionalInformationEmail() {
        return additionalInformationEmail;
    }

    public void setAdditionalInformationEmail(String additionalInformationEmail) {
        this.additionalInformationEmail = additionalInformationEmail;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    public void setAccountNonExpired(boolean accountNonExpired) {
        this.accountNonExpired = accountNonExpired;
    }

    public LocalDateTime getAccountLockedAt() {
        return accountLockedAt;
    }

    public void setAccountLockedAt(LocalDateTime accountLockedAt) {
        this.accountLockedAt = accountLockedAt;
    }

    public LocalDateTime getAccountLockedUntil() {
        return accountLockedUntil;
    }

    public void setAccountLockedUntil(LocalDateTime accountLockedUntil) {
        this.accountLockedUntil = accountLockedUntil;
    }

    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    public void setAccountNonLocked(boolean accountNonLocked) {
        this.accountNonLocked = accountNonLocked;
    }

    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    public void setCredentialsNonExpired(boolean credentialsNonExpired) {
        this.credentialsNonExpired = credentialsNonExpired;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public boolean isPreRegistration() {
        return preRegistration;
    }

    public void setPreRegistration(boolean preRegistration) {
        this.preRegistration = preRegistration;
    }

    public boolean isRegistrationCompleted() {
        return registrationCompleted;
    }

    public void setRegistrationCompleted(boolean registrationCompleted) {
        this.registrationCompleted = registrationCompleted;
    }

    public Boolean getNewsletter() {
        return newsletter;
    }

    public void setNewsletter(Boolean newsletter) {
        this.newsletter = newsletter;
    }

    public String getRegistrationUserUri() {
        return registrationUserUri;
    }

    public void setRegistrationUserUri(String registrationUserUri) {
        this.registrationUserUri = registrationUserUri;
    }

    public String getRegistrationAccessToken() {
        return registrationAccessToken;
    }

    public void setRegistrationAccessToken(String registrationAccessToken) {
        this.registrationAccessToken = registrationAccessToken;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public long getLoginsCount() {
        return loginsCount;
    }

    public void setLoginsCount(long loginsCount) {
        this.loginsCount = loginsCount;
    }

    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(LocalDateTime loggedAt) {
        this.loggedAt = loggedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getX509Certificates() {
        return x509Certificates;
    }

    public void setX509Certificates(String x509Certificates) {
        this.x509Certificates = x509Certificates;
    }

    public String getFactors() {
        return factors;
    }

    public void setFactors(String factors) {
        this.factors = factors;
    }

    public String getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(String additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    @Table("user_entitlements")
    public static class Entitlements {
        @Column("user_id")
        private String userId;
        private String entitlement;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getEntitlement() {
            return entitlement;
        }

        public void setEntitlement(String entitlement) {
            this.entitlement = entitlement;
        }
    }

    @Table("user_roles")
    public static class Role {
        @Column("user_id")
        private String userId;
        private String role;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    @Table("user_addresses")
    public static class Address {
        @Column("user_id")
        private String userId;
        private String type;
        private String formatted;
        @Column("street_address")
        private String streetAddress;
        private String locality;
        private String region;
        @Column("postal_code")
        private String postalCode;
        private String country;
        private Boolean primary;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getFormatted() {
            return formatted;
        }

        public void setFormatted(String formatted) {
            this.formatted = formatted;
        }

        public String getStreetAddress() {
            return streetAddress;
        }

        public void setStreetAddress(String streetAddress) {
            this.streetAddress = streetAddress;
        }

        public String getLocality() {
            return locality;
        }

        public void setLocality(String locality) {
            this.locality = locality;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public Boolean getPrimary() {
            return primary;
        }

        public void setPrimary(Boolean primary) {
            this.primary = primary;
        }
    }

    @Table("user_attributes")
    public static class Attribute {
        @Column("user_id")
        private String userId;
        @Column("user_field")
        private String userField;
        private String value;
        private String type;
        private Boolean primary;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUserField() {
            return userField;
        }

        public void setUserField(String userField) {
            this.userField = userField;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Boolean getPrimary() {
            return primary;
        }

        public void setPrimary(Boolean primary) {
            this.primary = primary;
        }
    }
}
