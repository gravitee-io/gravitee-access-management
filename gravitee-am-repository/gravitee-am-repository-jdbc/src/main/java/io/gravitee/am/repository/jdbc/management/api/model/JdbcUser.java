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

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Table("users")
public class JdbcUser extends AbstractUser {

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
    public static class Role extends AbstractRole {
    }

    @Table("dynamic_user_roles")
    public static class DynamicRole extends AbstractRole {
    }

    public static abstract class AbstractRole {
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

    @Table("user_identities")
    public static class Identity {
        @Column("user_id")
        private String userId;
        @Column("identity_id")
        private String identityId;
        @Column("provider_id")
        private String providerId;
        @Column("linked_at")
        private LocalDateTime linkedAt;
        @Column("additional_information")
        private String additionalInformation;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getIdentityId() {
            return identityId;
        }

        public void setIdentityId(String identityId) {
            this.identityId = identityId;
        }

        public String getProviderId() {
            return providerId;
        }

        public void setProviderId(String providerId) {
            this.providerId = providerId;
        }

        public LocalDateTime getLinkedAt() {
            return linkedAt;
        }

        public void setLinkedAt(LocalDateTime linkedAt) {
            this.linkedAt = linkedAt;
        }

        public String getAdditionalInformation() {
            return additionalInformation;
        }

        public void setAdditionalInformation(String additionalInformation) {
            this.additionalInformation = additionalInformation;
        }
    }
}
