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
package io.gravitee.am.service.model;

import static io.gravitee.am.service.authentication.crypto.password.PasswordValidator.PASSWORD_MAX_LENGTH;
import static io.gravitee.am.service.validators.EmailValidator.EMAIL_MAX_LENGTH;
import static io.gravitee.am.service.validators.EmailValidator.EMAIL_PATTERN;
import static io.gravitee.am.service.validators.UserValidator.*;

import java.util.Date;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class NewUser {

    @NotBlank
    @Size(max = DEFAULT_MAX_LENGTH)
    @Pattern(regexp = USERNAME_PATTERN, message = "invalid username")
    private String username;

    @Size(max = PASSWORD_MAX_LENGTH, message = "must not be greater than " + PASSWORD_MAX_LENGTH)
    private String password;

    @NotBlank
    @Pattern(regexp = EMAIL_PATTERN, message = "must be a well-formed email address")
    private String email;

    @Pattern(regexp = NAME_STRICT_PATTERN, message = "invalid first name")
    private String firstName;

    @Size(max = DEFAULT_MAX_LENGTH, message = "must not be greater than " + DEFAULT_MAX_LENGTH)
    @Pattern(regexp = NAME_STRICT_PATTERN, message = "invalid last name")
    private String lastName;

    @Size(max = DEFAULT_MAX_LENGTH, message = "must not be greater than " + DEFAULT_MAX_LENGTH)
    private String externalId;

    private boolean accountNonExpired = true;

    private boolean accountNonLocked = true;

    private boolean credentialsNonExpired = true;

    private boolean enabled = true;

    private boolean internal;

    private boolean preRegistration;

    private boolean registrationCompleted;

    private String domain;

    private String source;

    private String client;

    private Long loginsCount;

    private Date loggedAt;

    private Map<String, Object> additionalInformation;

    private Date createdAt;

    private Date updatedAt;

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
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
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

    public Long getLoginsCount() {
        return loginsCount;
    }

    public void setLoginsCount(Long loginsCount) {
        this.loginsCount = loginsCount;
    }

    public Date getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(Date loggedAt) {
        this.loggedAt = loggedAt;
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

    @Override
    public String toString() {
        return (
            "NewUser{" +
            "username='" +
            username +
            '\'' +
            ", email='" +
            email +
            '\'' +
            ", firstName='" +
            firstName +
            '\'' +
            ", lastName='" +
            lastName +
            '\'' +
            ", accountNonExpired=" +
            accountNonExpired +
            ", accountNonLocked=" +
            accountNonLocked +
            ", credentialsNonExpired=" +
            credentialsNonExpired +
            ", enabled=" +
            enabled +
            ", domain='" +
            domain +
            '\'' +
            ", source='" +
            source +
            '\'' +
            ", loginsCount=" +
            loginsCount +
            ", loggedAt=" +
            loggedAt +
            ", additionalInformation=" +
            additionalInformation +
            ", createdAt=" +
            createdAt +
            ", updatedAt=" +
            updatedAt +
            '}'
        );
    }
}
