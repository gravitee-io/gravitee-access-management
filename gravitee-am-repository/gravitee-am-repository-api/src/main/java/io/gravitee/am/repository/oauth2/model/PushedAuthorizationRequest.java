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

import java.util.Date;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Teams
 */
public class PushedAuthorizationRequest {

    /**
     * Technical ID
     */
    private String id;

    /**
     * Request domain
     */
    private String domain;

    /**
     * Technical identifier of the client which store this parameters
     */
    private String client;

    /**
     * The authorization parameters to preserve
     */
    private MultiValueMap<String,String> parameters;

    /**
     * The creation date
     */
    private Date createdAt;

    /**
     * The expiration date
     */
    private Date expireAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public MultiValueMap<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(MultiValueMap<String, String> parameters) {
        this.parameters = parameters;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Date expireAt) {
        this.expireAt = expireAt;
    }
}
