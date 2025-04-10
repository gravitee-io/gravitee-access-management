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

package io.gravitee.am.model.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class ClientSecret {
    private String id;
    private String settingsId;
    private String name;
    private String secret;
    @Schema(type = "java.lang.Long")
    private Date createdAt;

    public ClientSecret() {
    }

    public ClientSecret(String secret){
        this.secret = secret;
    }

    public ClientSecret(ClientSecret other) {
        this.id = other.id;
        this.settingsId = other.settingsId;
        this.name = other.name;
        this.secret = other.secret;
        this.createdAt = other.createdAt;
    }

    public ClientSecret safeSecret() {
        ClientSecret clientSecret = new ClientSecret(this);
        clientSecret.setSecret(null);
        return clientSecret;
    }
}
