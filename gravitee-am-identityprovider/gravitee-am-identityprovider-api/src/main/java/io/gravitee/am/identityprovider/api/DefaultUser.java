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
package io.gravitee.am.identityprovider.api;

import io.gravitee.am.common.oidc.StandardClaims;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultUser implements User {

    private String id;

    private String username;

    private String email;

    private String firstName;

    private String lastName;

    private String credentials;

    private boolean accountExpired = false;

    private boolean enabled = true;

    private List<String> roles;

    private Map<String, Object> additionalInformation;

    private Date createdAt;

    private Date updatedAt;

    private Date lastPasswordReset;

    public DefaultUser() {}

    public DefaultUser(String username) {
        this.username = username;
    }

    public DefaultUser(io.gravitee.am.model.User user) {
        if (user != null) {
            id = user.getExternalId();
            username = user.getUsername();
            email = user.getEmail();
            firstName = user.getFirstName();
            lastName = user.getLastName();
            additionalInformation = user.getAdditionalInformation();
            createdAt = user.getCreatedAt();
            updatedAt = user.getUpdatedAt();
            lastPasswordReset = user.getLastPasswordReset();
        }
    }

    public void setAccountExpired(boolean accountExpired) {
        this.accountExpired = accountExpired;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAdditionalInformation(Map<String, Object> additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getUsername() {
        if (username != null) {
            return username;
        }
        if (getAdditionalInformation() != null) {
            return (String) getAdditionalInformation().get(StandardClaims.PREFERRED_USERNAME);
        }
        return null;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getEmail() {
        if (email != null) {
            return email;
        }
        if (getAdditionalInformation() != null) {
            return (String) getAdditionalInformation().get(StandardClaims.EMAIL);
        }
        return null;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getFirstName() {
        if (firstName != null) {
            return firstName;
        }
        if (getAdditionalInformation() != null) {
            return (String) getAdditionalInformation().get(StandardClaims.GIVEN_NAME);
        }
        return null;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @Override
    public String getLastName() {
        if (lastName != null) {
            return lastName;
        }
        if (getAdditionalInformation() != null) {
            return (String) getAdditionalInformation().get(StandardClaims.FAMILY_NAME);
        }
        return null;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Override
    public String getCredentials() {
        return credentials;
    }

    public void setCredentials(String credentials) {
        this.credentials = credentials;
    }

    @Override
    public boolean isAccountExpired() {
        return accountExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Map<String, Object> getAdditionalInformation() {
        return additionalInformation;
    }

    @Override
    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public Date getLastPasswordReset() {
        return lastPasswordReset;
    }

    public void setLastPasswordReset(Date lastPasswordReset) {
        this.lastPasswordReset = lastPasswordReset;
    }

    @Override
    public boolean equals(Object rhs) {
        if (rhs instanceof User) {
            return username.equals(((User) rhs).getUsername());
        }
        return false;
    }

    /**
     * Returns the hashcode of the {@code username}.
     */
    @Override
    public int hashCode() {
        return username.hashCode();
    }

    @Override
    public String toString() {
        return this.username;
    }
}
