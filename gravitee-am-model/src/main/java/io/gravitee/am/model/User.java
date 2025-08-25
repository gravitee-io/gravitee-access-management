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
package io.gravitee.am.model;

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.model.scim.Certificate;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@ToString
public class User implements IUser {


    public static final Set<String> SENSITIVE_ADDITIONAL_PROPERTIES = Set.of(
            ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY,
            ConstantKeys.OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY
    );
    public static final String SENSITIVE_PROPERTY_PLACEHOLDER = "●●●●●●●●";
    private String id;

    private String externalId;

    private String username;

    private String password;

    private String email;

    private String displayName;

    private String nickName;

    private String firstName;

    private String lastName;

    private String title;

    private String type;

    private String preferredLanguage;

    private String picture;

    private List<Attribute> emails;

    private List<Attribute> phoneNumbers;

    private List<Attribute> ims;

    private List<Attribute> photos;

    private List<String> entitlements;

    private List<Address> addresses;

    private List<String> roles;

    private List<String> dynamicRoles;

    private List<String> dynamicGroups;

    private Set<Role> rolesPermissions;

    private List<String> groups;

    private List<Certificate> x509Certificates;

    private Boolean accountNonExpired = true;

    private Boolean accountNonLocked = true;

    @Schema(type = "java.lang.Long")
    private Date accountLockedAt;

    @Schema(type = "java.lang.Long")
    private Date accountLockedUntil;

    private Boolean credentialsNonExpired = true;

    private Boolean enabled = true;

    private Boolean internal = false;

    private Boolean preRegistration = false;

    private Boolean registrationCompleted = false;

    private Boolean newsletter;

    private String registrationUserUri;

    private String registrationAccessToken;

    private ReferenceType referenceType;

    private String referenceId;

    private String source;

    private String client;

    private Long loginsCount = 0L;

    private List<EnrolledFactor> factors;

    private List<UserIdentity> identities;

    private String lastIdentityUsed;

    private Map<String, Object> additionalInformation;

    @Schema(type = "java.lang.Long")
    private Date loggedAt;

    @Schema(type = "java.lang.Long")
    private Date lastLoginWithCredentials;

    @Schema(type = "java.lang.Long")
    private Date lastPasswordReset;

    @Schema(type = "java.lang.Long")
    private Date lastUsernameReset;

    @Schema(type = "java.lang.Long")
    private Date lastLogoutAt;

    @Schema(type = "java.lang.Long")
    private Date mfaEnrollmentSkippedAt;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    private Boolean forceResetPassword = Boolean.FALSE;

    private Boolean serviceAccount = Boolean.FALSE;

    public User() {

    }

    public User(boolean withDefaultValues) {

        if (!withDefaultValues) {
            this.accountNonExpired = null;
            this.accountNonLocked = null;
            this.credentialsNonExpired = null;
            this.enabled = null;
            this.internal = null;
            this.registrationCompleted = null;
            this.preRegistration = null;
            this.loginsCount = null;
            this.forceResetPassword = null;
        }
    }


    public User(User other) {
        this.id = other.id;
        this.externalId = other.externalId;
        this.username = other.username;
        this.password = other.password;
        this.email = other.email;
        this.displayName = other.displayName;
        this.nickName = other.nickName;
        this.firstName = other.firstName;
        this.lastName = other.lastName;
        this.title = other.title;
        this.type = other.type;
        this.preferredLanguage = other.preferredLanguage;
        this.picture = other.picture;
        this.emails = other.emails != null ? new ArrayList<>(other.emails) : null;
        this.phoneNumbers = other.phoneNumbers != null ? new ArrayList<>(other.phoneNumbers) : null;
        this.ims = other.ims != null ? new ArrayList<>(other.ims) : null;
        this.photos = other.photos != null ? new ArrayList<>(other.photos) : null;
        this.entitlements = other.entitlements != null ? new ArrayList<>(other.entitlements) : null;
        this.addresses = other.addresses != null ? new ArrayList<>(other.addresses) : null;
        this.roles = other.roles != null ? new ArrayList<>(other.roles) : null;
        this.dynamicRoles = other.dynamicRoles != null ? new ArrayList<>(other.dynamicRoles) : null;
        this.dynamicGroups = other.dynamicGroups != null ? new ArrayList<>(other.dynamicGroups) : null;
        this.rolesPermissions = other.rolesPermissions;
        this.x509Certificates = other.x509Certificates != null ? new ArrayList<>(other.x509Certificates) : null;
        this.accountNonExpired = other.accountNonExpired;
        this.accountNonLocked = other.accountNonLocked;
        this.accountLockedAt = other.accountLockedAt;
        this.accountLockedUntil = other.accountLockedUntil;
        this.credentialsNonExpired = other.credentialsNonExpired;
        this.enabled = other.enabled;
        this.internal = other.internal;
        this.preRegistration = other.preRegistration;
        this.registrationCompleted = other.registrationCompleted;
        this.newsletter = other.newsletter;
        this.registrationUserUri = other.registrationUserUri;
        this.registrationAccessToken = other.registrationAccessToken;
        this.referenceType = other.referenceType;
        this.referenceId = other.referenceId;
        this.source = other.source;
        this.client = other.client;
        this.loginsCount = other.loginsCount;
        this.factors = other.factors != null ? new ArrayList<>(other.factors) : null;
        this.identities = other.identities != null ? new ArrayList<>(other.identities) : null;
        this.lastIdentityUsed = other.lastIdentityUsed;
        this.additionalInformation = other.additionalInformation != null ? new HashMap<>(other.additionalInformation) : null;
        this.loggedAt = other.loggedAt;
        this.lastLoginWithCredentials = other.lastLoginWithCredentials;
        this.lastPasswordReset = other.lastPasswordReset;
        this.lastLogoutAt = other.lastLogoutAt;
        this.mfaEnrollmentSkippedAt = other.mfaEnrollmentSkippedAt;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
        this.lastUsernameReset = other.lastUsernameReset;
        this.forceResetPassword = other.forceResetPassword;
    }

    public static User simpleUser(String userId, ReferenceType referenceType, String referenceId) {
        User user = new User();
        user.setId(userId);
        user.setReferenceType(referenceType);
        user.setReferenceId(referenceId);
        return user;
    }

    public UserId getFullId() {
        return new UserId(id, externalId, source);
    }

    public String getEmail() {
        if (email == null && getAdditionalInformation() != null && getAdditionalInformation().get(StandardClaims.EMAIL) != null) {
            return (String) getAdditionalInformation().get(StandardClaims.EMAIL);
        }
        return email;
    }

    public String getDisplayName() {
        if (displayName != null) {
            return displayName;
        }

        // fall back to combination of first name and last name
        if (getFirstName() != null) {
            return getFirstName() + ((getLastName() != null) ? " " + getLastName() : "");
        }

        // fall back to standard claims
        if (getAdditionalInformation() != null) {
            if (getAdditionalInformation().get(StandardClaims.NAME) != null) {
                return (String) getAdditionalInformation().get(StandardClaims.NAME);
            }
            if (getAdditionalInformation().get(StandardClaims.GIVEN_NAME) != null) {
                return getAdditionalInformation().get(StandardClaims.GIVEN_NAME) + ((getAdditionalInformation().get(StandardClaims.FAMILY_NAME) != null) ? " " + getAdditionalInformation().get(StandardClaims.FAMILY_NAME) : "");
            }
        }

        // default display the username
        return username;
    }

    public String getFirstName() {
        if (firstName == null && getAdditionalInformation() != null && getAdditionalInformation().get(StandardClaims.GIVEN_NAME) != null) {
            // fall back to OIDC standard claims
            firstName = (String) getAdditionalInformation().get(StandardClaims.GIVEN_NAME);
        }

        return firstName;
    }

    public String getMiddleName() {
        if (getAdditionalInformation() != null && getAdditionalInformation().get(StandardClaims.MIDDLE_NAME) != null) {
            return (String) getAdditionalInformation().get(StandardClaims.MIDDLE_NAME);
        }
        return null;
    }

    public void setMiddleName(String middleName) {
        putAdditionalInformation(StandardClaims.MIDDLE_NAME, middleName);
    }

    public String getLastName() {
        if (lastName == null && getAdditionalInformation() != null && getAdditionalInformation().get(StandardClaims.FAMILY_NAME) != null) {
            // fall back to OIDC standard claims
            lastName = (String) getAdditionalInformation().get(StandardClaims.FAMILY_NAME);
        }

        return lastName;
    }

    public String getPicture() {
        if (picture == null) {
            if (photos != null && !photos.isEmpty()) {
                // fall back to SCIM photos
                picture = photos.stream()
                        .filter(p -> Boolean.TRUE.equals(p.isPrimary()))
                        .map(Attribute::getValue)
                        .findFirst()
                        .orElse(photos.get(0).getValue());
            } else if (getAdditionalInformation() != null && getAdditionalInformation().get(StandardClaims.PICTURE) != null) {
                // fall back to OIDC standard claims
                picture = (String) getAdditionalInformation().get(StandardClaims.PICTURE);
            }
        }
        return picture;
    }

    public Boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    public Boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    public Boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public Boolean isInternal() {
        return internal;
    }

    public Boolean isPreRegistration() {
        return preRegistration;
    }

    public Boolean isRegistrationCompleted() {
        return registrationCompleted;
    }

    public Boolean isNewsletter() {
        return newsletter;
    }


    public String getLastIdentityUsed() {
        return lastIdentityUsed != null ? lastIdentityUsed : source;
    }


    public Map<String, Object> getLastIdentityInformation() {
        if (this.lastIdentityUsed != null && this.identities != null) {
            return this.identities.stream()
                    .filter(userIdentity -> this.lastIdentityUsed.equals(userIdentity.getProviderId()))
                    .findFirst()
                    .map(UserIdentity::getAdditionalInformation)
                    .orElse(getAdditionalInformation());
        }
        return getAdditionalInformation();
    }

    public Map<String, Object> getIdentitiesAsMap() {
        if (this.identities != null) {
            return this.identities.stream().collect(Collectors.toMap(UserIdentity::getProviderId, Function.identity()));
        }
        return Map.of();
    }

    public User putAdditionalInformation(String key, Object value) {
        if (getAdditionalInformation() == null) {
            setAdditionalInformation(new HashMap<>());
        }
        additionalInformation.put(key, value);
        return this;
    }

    public User removeAdditionalInformation(String key) {
        if (getAdditionalInformation() != null) {
            additionalInformation.remove(key);
        }
        return this;
    }

    public <T> T get(String claim) {
        if (getAdditionalInformation() != null) {
            return (T) getAdditionalInformation().get(claim);
        }
        return null;
    }

    public void setAdditionalInformation(Map<String, Object> additionalInformation) {
        if (additionalInformation == null) {
            this.additionalInformation = null;
            return;
        }
        this.additionalInformation = additionalInformation
                .entrySet()
                .stream()
                .map(e -> {
                    var isHiddenSensitiveValue = SENSITIVE_ADDITIONAL_PROPERTIES.contains(e.getKey()) && SENSITIVE_PROPERTY_PLACEHOLDER.equals(e.getValue());
                    var propertyExistedBefore = this.additionalInformation != null && this.additionalInformation.containsKey(e.getKey());
                    if (isHiddenSensitiveValue && propertyExistedBefore) {
                        // the value existed before and is not being updated right now - keep old value
                        return Map.entry(e.getKey(), this.additionalInformation.get(e.getKey()));
                    } else {
                        return e;
                    }
                })
                // Collectors.toMap() doesn't work if there's any null values, and we don't have a guarantee there aren't any
                .collect(HashMap<String, Object>::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll);
    }

    public Date getLastPasswordReset() {
        return lastPasswordReset;
    }

    public void setLastPasswordReset(Date lastPasswordReset) {
        this.lastPasswordReset = lastPasswordReset;
    }

    public Date getLastUsernameReset() {
        return lastUsernameReset;
    }

    public void setLastUsernameReset(Date lastUsernameReset) {
        this.lastUsernameReset = lastUsernameReset;
    }

    public Date getLastLogoutAt() {
        return lastLogoutAt;
    }

    public void setLastLogoutAt(Date lastLogoutAt) {
        this.lastLogoutAt = lastLogoutAt;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean isInactive() {
        return isPreRegistration() && !isRegistrationCompleted();
    }

    public String getProfile() {
        return get(StandardClaims.PROFILE);
    }

    public void setProfile(String profile) {
        putAdditionalInformation(StandardClaims.PROFILE, profile);
    }

    public String getWebsite() {
        return get(StandardClaims.WEBSITE);
    }

    public void setWebsite(String website) {
        putAdditionalInformation(StandardClaims.WEBSITE, website);
    }

    public String getBirthdate() {
        return get(StandardClaims.BIRTHDATE);
    }

    public void setBirthdate(String birthdate) {
        putAdditionalInformation(StandardClaims.BIRTHDATE, birthdate);
    }

    public String getZoneInfo() {
        return get(StandardClaims.ZONEINFO);
    }

    public void setZoneInfo(String zoneInfo) {
        putAdditionalInformation(StandardClaims.ZONEINFO, zoneInfo);
    }

    public String getLocale() {
        return get(StandardClaims.LOCALE);
    }

    public void setLocale(String locale) {
        putAdditionalInformation(StandardClaims.LOCALE, locale);
    }

    public String getPhoneNumber() {
        return get(StandardClaims.PHONE_NUMBER);
    }

    public void setPhoneNumber(String phoneNumber) {
        putAdditionalInformation(StandardClaims.PHONE_NUMBER, phoneNumber);
    }

    public Map<String, Object> getAddress() {
        if (getAdditionalInformation() != null && getAdditionalInformation().get(StandardClaims.ADDRESS) != null) {
            return (Map<String, Object>) getAdditionalInformation().get(StandardClaims.ADDRESS);
        }
        return null;
    }

    public void setAddress(Map<String, Object> address) {
        putAdditionalInformation(StandardClaims.ADDRESS, address);
    }

    public void updateUsername(String username) {
        setUsername(username);
        setLastUsernameReset(new Date());
        unlockUser();
    }

    public void unlockUser() {
        setAccountNonLocked(true);
        setAccountLockedAt(null);
        setAccountLockedUntil(null);
    }

    public boolean isDisabled(){
        return Boolean.FALSE.equals(enabled);
    }
}
