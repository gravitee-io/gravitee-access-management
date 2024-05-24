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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
@NoArgsConstructor
public class LoginAttempt {

    private String id;
    private String domain;
    private String client;
    private String identityProvider;
    private String username;
    private Set<Identity> linkedIdentities;
    private int attempts;
    @Schema(type = "java.lang.Long")
    private Date expireAt;
    @Schema(type = "java.lang.Long")
    private Date createdAt;
    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    public boolean isAccountLocked(int maxAttempts) {
        return attempts >= maxAttempts;
    }

    public void setLinkedUserIdentities(List<UserIdentity> linkedIdentities) {
        this.linkedIdentities = linkedIdentities
                .stream()
                .map(identity -> new Identity(identity.getUserId(), identity.getProviderId()))
                .collect(Collectors.toSet());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Identity {
        private String username;
        private String identityProvider;
    }
}
