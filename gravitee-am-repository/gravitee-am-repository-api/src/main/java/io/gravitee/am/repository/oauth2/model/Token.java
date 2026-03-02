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
package io.gravitee.am.repository.oauth2.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.Objects;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public abstract class Token {

    /**
     * Technical ID
     */
    private String id;

    /**
     * Token value
     */
    private String token;

    /**
     * Token domain
     */
    private String domain;

    /**
     * Technical identifier of the client which ask for this token
     */
    private String client;

    /**
     * Technical identifier of the end-user.
     */
    private String subject;

    /**
     * The token creation date
     */
    private Date createdAt;

    /**
     * The token expiration date
     */

    private Date expireAt;

    /**
     * The parent subject JWT jti claim
     */

    private String parentSubjectJti;

    /**
     * The parent actor JWT jti claim
     */

    private String parentActorJti;



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return Objects.equals(id, token.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
