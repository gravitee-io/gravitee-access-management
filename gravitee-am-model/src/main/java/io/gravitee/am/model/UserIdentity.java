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


import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * User identity object when accounts are linked.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserIdentity {

    private String userId;

    private String username;

    private String providerId;

    private Map<String, Object> additionalInformation;

    @Schema(type = "java.lang.Long")
    private Date linkedAt;

    public UserIdentity() {}

    public UserIdentity(UserIdentity other) {
        this.userId = other.userId;
        this.username = other.username;
        this.providerId = other.providerId;
        this.additionalInformation = other.additionalInformation != null ? new HashMap<>(other.additionalInformation) : null;
        this.linkedAt = other.linkedAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public Map<String, Object> getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(Map<String, Object> additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public Date getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(Date linkedAt) {
        this.linkedAt = linkedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserIdentity that = (UserIdentity) o;
        return Objects.equals(userId, that.userId) && Objects.equals(providerId, that.providerId) && Objects.equals(additionalInformation, that.additionalInformation) && Objects.equals(linkedAt, that.linkedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, providerId, additionalInformation, linkedAt);
    }
}
