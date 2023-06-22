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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.UserIdentity;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.repository.mongodb.common.model.Auditable;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.AddressMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.AttributeMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.CertificateMongo;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.Date;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserMongo extends Auditable {

    @BsonId
    private String id;
    private String externalId;
    private String username;
    private String email;
    private String displayName;
    private String nickName;
    private String firstName;
    private String lastName;
    private String title;
    private String type;
    private String preferredLanguage;
    private boolean accountNonExpired = true;
    private Date accountLockedAt;
    private Date accountLockedUntil;
    private boolean accountNonLocked = true;
    private boolean credentialsNonExpired = true;
    private boolean enabled = true;
    private boolean internal;
    private boolean preRegistration;
    private boolean registrationCompleted;
    private Boolean newsletter;
    private String registrationUserUri;
    private String registrationAccessToken;
    private String referenceType;
    private String referenceId;
    private String source;
    private String client;
    private long loginsCount;
    private Date loggedAt;
    private Date lastLoginWithCredentials;
    private Date mfaEnrollmentSkippedAt;
    private Date lastPasswordReset;
    private Date lastLogoutAt;
    private Date lastUsernameReset;
    private List<AttributeMongo> emails;
    private List<AttributeMongo> phoneNumbers;
    private List<AttributeMongo> ims;
    private List<AttributeMongo> photos;
    private List<String> entitlements;
    private List<AddressMongo> addresses;
    private List<CertificateMongo> x509Certificates;
    private List<EnrolledFactor> factors;
    private List<String> roles;
    private List<String> dynamicRoles;
    private List<UserIdentity> identities;
    private String lastIdentityUsed;
    /**
     * Map codec support is planned for version 3.7 jira.mongodb.org issue: JAVA-2695
     */
    private Document additionalInformation;

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

    public Date getLastPasswordReset() {
        return lastPasswordReset;
    }

    public void setLastPasswordReset(Date lastPasswordReset) {
        this.lastPasswordReset = lastPasswordReset;
    }

    public Date getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(Date loggedAt) {
        this.loggedAt = loggedAt;
    }

    public Date getLastLoginWithCredentials() {
        return lastLoginWithCredentials;
    }

    public void setLastLoginWithCredentials(Date lastLoginWithCredentials) {
        this.lastLoginWithCredentials = lastLoginWithCredentials;
    }

    public Date getMfaEnrollmentSkippedAt() {
        return mfaEnrollmentSkippedAt;
    }

    public void setMfaEnrollmentSkippedAt(Date mfaEnrollmentSkippedAt) {
        this.mfaEnrollmentSkippedAt = mfaEnrollmentSkippedAt;
    }

    public Date getLastLogoutAt() {
        return lastLogoutAt;
    }

    public void setLastLogoutAt(Date lastLogoutAt) {
        this.lastLogoutAt = lastLogoutAt;
    }

    public List<AttributeMongo> getEmails() {
        return emails;
    }

    public void setEmails(List<AttributeMongo> emails) {
        this.emails = emails;
    }

    public List<AttributeMongo> getPhoneNumbers() {
        return phoneNumbers;
    }

    public void setPhoneNumbers(List<AttributeMongo> phoneNumbers) {
        this.phoneNumbers = phoneNumbers;
    }

    public List<AttributeMongo> getIms() {
        return ims;
    }

    public void setIms(List<AttributeMongo> ims) {
        this.ims = ims;
    }

    public List<AttributeMongo> getPhotos() {
        return photos;
    }

    public void setPhotos(List<AttributeMongo> photos) {
        this.photos = photos;
    }

    public List<String> getEntitlements() {
        return entitlements;
    }

    public void setEntitlements(List<String> entitlements) {
        this.entitlements = entitlements;
    }

    public List<AddressMongo> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<AddressMongo> addresses) {
        this.addresses = addresses;
    }

    public List<CertificateMongo> getX509Certificates() {
        return x509Certificates;
    }

    public void setX509Certificates(List<CertificateMongo> x509Certificates) {
        this.x509Certificates = x509Certificates;
    }

    public List<EnrolledFactor> getFactors() {
        return factors;
    }

    public void setFactors(List<EnrolledFactor> factors) {
        this.factors = factors;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getDynamicRoles() {
        return dynamicRoles;
    }

    public void setDynamicRoles(List<String> dynamicRoles) {
        this.dynamicRoles = dynamicRoles;
    }

    public List<UserIdentity> getIdentities() {
        return identities;
    }

    public void setIdentities(List<UserIdentity> identities) {
        this.identities = identities;
    }

    public String getLastIdentityUsed() {
        return lastIdentityUsed;
    }

    public void setLastIdentityUsed(String lastIdentityUsed) {
        this.lastIdentityUsed = lastIdentityUsed;
    }

    public Document getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(Document additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public Date getLastUsernameReset() {
        return lastUsernameReset;
    }

    public void setLastUsernameReset(Date lastUsernameReset) {
        this.lastUsernameReset = lastUsernameReset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserMongo userMongo = (UserMongo) o;

        return id != null ? id.equals(userMongo.id) : userMongo.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
