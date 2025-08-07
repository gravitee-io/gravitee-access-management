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
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Setter
@Getter
public class ExtensionGrant {

    private String id;

    private String name;

    private String type;

    private String configuration;

    private String domain;

    private String grantType;

    private String identityProvider;

    private boolean createUser;

    private boolean userExists;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    public ExtensionGrant() {
    }

    public ExtensionGrant(ExtensionGrant other) {
        this.id = other.id;
        this.name = other.name;
        this.type = other.type;
        this.configuration = other.configuration;
        this.domain = other.domain;
        this.grantType = other.grantType;
        this.identityProvider = other.identityProvider;
        this.createUser = other.createUser;
        this.userExists = other.userExists;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

}
