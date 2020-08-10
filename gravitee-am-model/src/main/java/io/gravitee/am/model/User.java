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
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.model.scim.Certificate;

import java.util.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class User {

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

    private boolean accountNonExpired = true;

    private boolean accountNonLocked = true;

    private Date accountLockedAt;

    private Date accountLockedUntil;

    private boolean credentialsNonExpired = true;

    private boolean enabled = true;

    private boolean internal;

    private boolean preRegistration;

    private boolean registrationCompleted;

    private String registrationUserUri;

    private String registrationAccessToken;

    private String domain;

    private String source;

    private String client;

    private long loginsCount;

    private Map<String, Object> additionalInformation;

    private Date loggedAt;

    private Date createdAt;

    private Date updatedAt;

    public User() {
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
        this.registrationUserUri = other.registrationUserUri;
        this.registrationAccessToken = other.registrationAccessToken;
        this.domain = other.domain;
        this.source = other.source;
        this.client = other.client;
        this.loginsCount = other.loginsCount;
        this.additionalInformation = other.additionalInformation != null ? new HashMap<>(other.additionalInformation) : null;
        this.loggedAt = other.loggedAt;
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
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
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

    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    public void setAccountNonExpired(boolean accountNonExpired) {
        this.accountNonExpired = accountNonExpired;
    }

    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    public void setAccountNonLocked(boolean accountNonLocked) {
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

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
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

    public void setAdditionalInformation(Map<String, Object> additionalInformation) {
        this.additionalInformation = additionalInformation;
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

    public boolean isInactive() {
        return isPreRegistration() && !isRegistrationCompleted();
    }
}
