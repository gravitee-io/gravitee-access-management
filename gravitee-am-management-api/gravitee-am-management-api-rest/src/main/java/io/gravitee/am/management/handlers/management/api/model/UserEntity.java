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
package io.gravitee.am.management.handlers.management.api.model;

import io.gravitee.am.model.User;
import lombok.Getter;
import lombok.Setter;
<<<<<<< HEAD
=======

import java.util.Map;
import java.util.stream.Collectors;
>>>>>>> 95f146351f (fix: hide sensitive additional properties from api)


/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class UserEntity extends User {
    private ApplicationEntity applicationEntity;

    private String sourceId;

    public UserEntity(User user) {
        setId(user.getId());
        setExternalId(user.getExternalId());
        setUsername(user.getUsername());
        setPassword(null);
        setEmail(user.getEmail());
        setDisplayName(user.getDisplayName());
        setNickName(user.getNickName());
        setFirstName(user.getFirstName());
        setLastName(user.getLastName());
        setTitle(user.getTitle());
        setPicture(user.getPicture());
        setAccountNonExpired(user.isAccountNonExpired());
        setAccountNonLocked(user.isAccountNonLocked());
        setAccountLockedAt(user.getAccountLockedAt());
        setAccountLockedUntil(user.getAccountLockedUntil());
        setCredentialsNonExpired(user.isCredentialsNonExpired());
        setEnabled(user.isEnabled());
        setInternal(user.isInternal());
        setPreRegistration(user.isPreRegistration());
        setRegistrationCompleted(user.isRegistrationCompleted());
        setReferenceType(user.getReferenceType());
        setReferenceId(user.getReferenceId());
        setSource(user.getSource());
        setClient(user.getClient());
        setLoginsCount(user.getLoginsCount());
        setLoggedAt(user.getLoggedAt());
        setRoles(user.getRoles());
        setDynamicRoles(user.getDynamicRoles());
        setRolesPermissions(user.getRolesPermissions());
        setAdditionalInformation(filterSensitiveInfo(user.getAdditionalInformation()));
        setCreatedAt(user.getCreatedAt());
        setUpdatedAt(user.getUpdatedAt());
        setLastPasswordReset(user.getLastPasswordReset());
        setLastIdentityUsed(user.getLastIdentityUsed());
        setForceResetPassword(user.getForceResetPassword());
        setServiceAccount(user.getServiceAccount());
        this.sourceId = user.getSource();
    }
<<<<<<< HEAD
=======

    private Map<String, Object> filterSensitiveInfo(Map<String, Object> additionalInformation) {
        if (additionalInformation == null) {
            return null;
        }
        return additionalInformation.entrySet()
                .stream()
                .map(e -> SENSITIVE_ADDITIONAL_PROPERTIES.contains(e.getKey()) ? Map.entry(e.getKey(), SENSITIVE_PROPERTY_PLACEHOLDER) : e)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
<<<<<<< HEAD


    public record AdditionalProperty(@JsonIgnore Object value, boolean sensitive, boolean isNewValue) {

        @JsonProperty("value")
        public Object getValue() {
            return sensitive ? null : value;
        }

        static Map<String, Object> wrap(Map<String, Object> properties) {
            return properties.entrySet()
                    .stream()
                    .map(AdditionalProperty::wrap)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        private static Map.Entry<String, AdditionalProperty> wrap(Map.Entry<String, Object> e) {
            return Map.entry(e.getKey(), new AdditionalProperty(e.getValue(), SENSITIVE_ADDITIONAL_PROPERTIES.contains(e.getKey()), false));
        }
    }

>>>>>>> 95f146351f (fix: hide sensitive additional properties from api)
=======
>>>>>>> c6ae6aa83b (fix: code cleanup, unit tests)
}
