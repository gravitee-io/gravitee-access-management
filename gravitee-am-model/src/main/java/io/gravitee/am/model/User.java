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
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.model.scim.Certificate;

import java.util.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class User implements IUser {

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

    private Set<Role> rolesPermissions;

    private List<String> groups;

    private List<Certificate> x509Certificates;

    private Boolean accountNonExpired = true;

    private Boolean accountNonLocked = true;

    private Date accountLockedAt;

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

    private Map<String, Object> additionalInformation;

    private Date loggedAt;

    private Date lastPasswordReset;

    private Date createdAt;

    private Date updatedAt;

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
        this.additionalInformation = other.additionalInformation != null ? new HashMap<>(other.additionalInformation) : null;
        this.loggedAt = other.loggedAt;
        this.lastPasswordReset = other.lastPasswordReset;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        if (email == null) {
            // fall back to standard claims
            if (getAdditionalInformation() != null) {
                if (getAdditionalInformation().get(StandardClaims.EMAIL) != null) {
                    return (String) getAdditionalInformation().get(StandardClaims.EMAIL);
                }
            }
        }
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
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

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getFirstName() {
        if (firstName == null) {
            if (getAdditionalInformation() != null && getAdditionalInformation().get(StandardClaims.GIVEN_NAME) != null) {
                // fall back to OIDC standard claims
                firstName = (String) getAdditionalInformation().get(StandardClaims.GIVEN_NAME);
            }
        }
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
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
        if (lastName == null) {
            if (getAdditionalInformation() != null && getAdditionalInformation().get(StandardClaims.FAMILY_NAME) != null) {
                // fall back to OIDC standard claims
                lastName = (String) getAdditionalInformation().get(StandardClaims.FAMILY_NAME);
            }
        }
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
        if (preferredLanguage == null) {
            // fall back to OIDC standard claims
            if (getAdditionalInformation() != null && getAdditionalInformation().get(StandardClaims.LOCALE) != null) {
                preferredLanguage = (String) getAdditionalInformation().get(StandardClaims.LOCALE);
            }
        }
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
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

    public void setPicture(String picture) {
        this.picture = picture;
    }

    public List<Attribute> getEmails() {
        return emails;
    }

    public void setEmails(List<Attribute> emails) {
        this.emails = emails;
    }

    public List<Attribute> getPhoneNumbers() {
        return phoneNumbers;
    }

    public void setPhoneNumbers(List<Attribute> phoneNumbers) {
        this.phoneNumbers = phoneNumbers;
    }

    public List<Attribute> getIms() {
        return ims;
    }

    public void setIms(List<Attribute> ims) {
        this.ims = ims;
    }

    public List<Attribute> getPhotos() {
        return photos;
    }

    public void setPhotos(List<Attribute> photos) {
        this.photos = photos;
    }

    public List<String> getEntitlements() {
        return entitlements;
    }

    public void setEntitlements(List<String> entitlements) {
        this.entitlements = entitlements;
    }

    public List<Address> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<Address> addresses) {
        this.addresses = addresses;
    }

    public List<Certificate> getX509Certificates() {
        return x509Certificates;
    }

    public void setX509Certificates(List<Certificate> x509Certificates) {
        this.x509Certificates = x509Certificates;
    }

    public Boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    public void setAccountNonExpired(Boolean accountNonExpired) {
        this.accountNonExpired = accountNonExpired;
    }

    public Boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    public void setAccountNonLocked(Boolean accountNonLocked) {
        this.accountNonLocked = accountNonLocked;
    }

    public Date getAccountLockedAt() {
        return accountLockedAt;
    }

    public void setAccountLockedAt(Date accountLockedAt) {
        this.accountLockedAt = accountLockedAt;
    }

    public Date getAccountLockedUntil() {
        return accountLockedUntil;
    }

    public void setAccountLockedUntil(Date accountLockedUntil) {
        this.accountLockedUntil = accountLockedUntil;
    }

    public Boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    public void setCredentialsNonExpired(Boolean credentialsNonExpired) {
        this.credentialsNonExpired = credentialsNonExpired;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean isInternal() {
        return internal;
    }

    public void setInternal(Boolean internal) {
        this.internal = internal;
    }

    public Boolean isPreRegistration() {
        return preRegistration;
    }

    public void setPreRegistration(Boolean preRegistration) {
        this.preRegistration = preRegistration;
    }

    public Boolean isRegistrationCompleted() {
        return registrationCompleted;
    }

    public void setRegistrationCompleted(Boolean registrationCompleted) {
        this.registrationCompleted = registrationCompleted;
    }

    public Boolean isNewsletter() {
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

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
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

    public Long getLoginsCount() {
        return loginsCount;
    }

    public void setLoginsCount(Long loginsCount) {
        this.loginsCount = loginsCount;
    }

    public List<EnrolledFactor> getFactors() {
        return factors;
    }

    public void setFactors(List<EnrolledFactor> factors) {
        this.factors = factors;
    }

    public Date getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(Date loggedAt) {
        this.loggedAt = loggedAt;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public Set<Role> getRolesPermissions() {
        return rolesPermissions;
    }

    public void setRolesPermissions(Set<Role> rolesPermissions) {
        this.rolesPermissions = rolesPermissions;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public Map<String, Object> getAdditionalInformation() {
        return additionalInformation;
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
        this.additionalInformation = additionalInformation;
    }

    public Date getLastPasswordReset() {
        return lastPasswordReset;
    }

    public void setLastPasswordReset(Date lastPasswordReset) {
        this.lastPasswordReset = lastPasswordReset;
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
}
