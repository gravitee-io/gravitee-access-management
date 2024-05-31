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
package io.gravitee.am.model.safe;

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserIdentity;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserProperties {

    private String id;
    private String externalId;
    private String domain;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String source;
    private String preferredLanguage;
    private Set<String> roles;
    private List<String> groups;
    private Map<String, Object> claims;
    private Map<String, Object> additionalInformation;
    private List<Attribute> emails;
    private List<Attribute> phoneNumbers;
    private List<Attribute> ims;
    private List<Attribute> photos;
    private List<String> entitlements;
    private List<Address> addresses;
    private List<UserIdentity> identities;
    private String lastIdentityUsed;

    public UserProperties() {
    }

    public UserProperties(User user) {
        this.id = user.getId();
        this.externalId = user.getExternalId();

        if(user.getReferenceType() == ReferenceType.DOMAIN) {
            this.domain = user.getReferenceId();
        }

        this.username = user.getUsername();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.phoneNumber = user.getPhoneNumber();
        // set groups
        this.groups = user.getGroups();
        // set roles
        if (user.getRolesPermissions() != null) {
            roles = user.getRolesPermissions().stream().map(Role::getName).collect(Collectors.toSet());
        }
        // set claims
        var userAdditionalInformation = ofNullable(user.getAdditionalInformation())
                .orElse(new HashMap<>());
        claims = new HashMap<>(userAdditionalInformation);
        if (user.getLoggedAt() != null) {
            claims.put(Claims.AUTH_TIME, user.getLoggedAt().getTime() / 1000);
        }

        removeSensitiveClaims(claims);

        this.additionalInformation = claims; // use same ref as claims for additionalInfo to avoid regression on templates that used the User object before
        this.source = user.getSource();
        this.preferredLanguage = evaluatePreferredLanguage(user);
        this.emails = evaluateAttributes(user.getEmails());
        this.phoneNumbers = evaluateAttributes(user.getPhoneNumbers());
        this.ims = evaluateAttributes(user.getIms());
        this.photos = evaluateAttributes(user.getPhotos());
        this.entitlements = user.getEntitlements();
        this.addresses = evaluateAddresses(user.getAddresses());

        this.lastIdentityUsed = user.getLastIdentityUsed();
        this.identities = ofNullable(user.getIdentities()).map(identities ->
                identities.stream().map(sourceIdentity -> {
                    // filter sensitive date from the additionalInformation map linked
                    // to the UserIdentity object
                    var filteredIdentity = new UserIdentity(sourceIdentity);
                    removeSensitiveClaims(filteredIdentity.getAdditionalInformation());
                    return filteredIdentity;
                }).collect(toList())).orElse(List.of());
    }

    private void removeSensitiveClaims(Map claimsToClean) {
        // remove technical information that shouldn't be used in templates
        claimsToClean.remove(ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY);
        claimsToClean.remove(ConstantKeys.OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY);
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

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }

    public void setClaims(Map<String, Object> claims) {
        this.claims = claims;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public Map<String, Object> getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(Map<String, Object> additionalInformation) {
        this.additionalInformation = additionalInformation;
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

    private String evaluatePreferredLanguage(User user) {
        if (user.getPreferredLanguage() == null) {
            // fall back to OIDC standard claims
            if (user.getAdditionalInformation() != null && user.getAdditionalInformation().get(StandardClaims.LOCALE) != null) {
                return (String) user.getAdditionalInformation().get(StandardClaims.LOCALE);
            }
        }
        return user.getPreferredLanguage();
    }

    private List<Attribute> evaluateAttributes(List<Attribute> attributes) {
        if (attributes == null) {
            return null;
        }
        // Nimbus internal JSON writer do not handle null values
        return attributes
                .stream()
                .map(attribute -> {
                    attribute.setPrimary(Boolean.TRUE.equals(attribute.isPrimary()));
                    return attribute;
                }).collect(toList());

    }

    private List<Address> evaluateAddresses(List<Address> addresses) {
        if (addresses == null) {
            return null;
        }
        // Nimbus internal JSON writer do not handle null values
        return addresses
                .stream()
                .map(address -> {
                    address.setPrimary(Boolean.TRUE.equals(address.isPrimary()));
                    return address;
                }).collect(toList());
    }
}
