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

import io.gravitee.common.util.MultiValueMap;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */

@Getter
@Setter
public class AuthorizationCode {

    /**
     * Technical ID
     */
    private String id;

    /**
     * Transaction ID
     */
    private String transactionId;

    /**
     * Context version
     */
    private int contextVersion;

    /**
     * Authorization code value
     */
    private String code;

    /**
     * The authorization code creation date
     */
    private Date createdAt;

    /**
     * The authorization code expiration date
     */
    private Date expireAt;

    /**
     * The client which asks for the authorization code
     */
    private String clientId;

    /**
     * Technical identifier for logged user
     */
    private String subject;

    /**
     * Redirect URI used while asking for an authorization code
     */
    private String redirectUri;

    /**
     * The scopes of the access request
     */
    private Set<String> scopes;

    /**
     * The resource identifier for which the authorization code is requested
     */
    private Set<String> resources;

    /**
     * The Authorization request parameters
     */
    private MultiValueMap<String,String> requestParameters;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AuthorizationCode that = (AuthorizationCode) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
