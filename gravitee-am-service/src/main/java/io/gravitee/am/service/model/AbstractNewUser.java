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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.gravitee.am.model.IUser;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;
import java.util.Map;
@Getter
@Setter
@ToString
public abstract class AbstractNewUser implements IUser {

    @NotBlank
    private String username;
    @ToString.Exclude
    private String password;

    private String firstName;

    private String lastName;
    @ToString.Exclude
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

    @ToString.Exclude
    private String client;

    private Long loginsCount;

    @Schema(type = "java.lang.Long")
    private Date loggedAt;

    private String preferredLanguage;

    private Map<String, Object> additionalInformation;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    private Boolean forceResetPassword;

    @Schema(type = "java.lang.Long")
    private Date lastPasswordReset;

    @Override
    @JsonIgnore
    public String getDisplayName() {
        return null;
    }

    @Override
    @JsonIgnore
    public String getNickName() {
        return null;
    }
}
