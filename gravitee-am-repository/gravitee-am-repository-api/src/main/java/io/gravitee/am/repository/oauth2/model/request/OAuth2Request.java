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
package io.gravitee.am.repository.oauth2.model.request;

import io.gravitee.am.repository.oauth2.model.authority.GrantedAuthority;

import java.io.Serializable;
import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2Request extends BaseRequest {

    private static final long serialVersionUID = 1106967416000265157L;

    private Set<String> resourceIds = new HashSet<>();

    private Collection<? extends GrantedAuthority> authorities = new HashSet<>();

    private boolean approved = false;

    private TokenRequest refresh = null;

    private String redirectUri;

    private Set<String> responseTypes = new HashSet<>();

    private Map<String, Serializable> extensions = new HashMap<>();

    public OAuth2Request() {}

    public OAuth2Request(Map<String, String> requestParameters, String clientId,
                         Collection<? extends GrantedAuthority> authorities, boolean approved, Set<String> scope,
                         Set<String> resourceIds, String redirectUri, Set<String> responseTypes,
                         Map<String, Serializable> extensionProperties) {
        setClientId(clientId);
        setRequestParameters(requestParameters);
        setScope(scope);
        if (resourceIds != null) {
            this.resourceIds = new HashSet<>(resourceIds);
        }
        if (authorities != null) {
            this.authorities = new HashSet<>(authorities);
        }
        this.approved = approved;
        if (responseTypes != null) {
            this.responseTypes = new HashSet<>(responseTypes);
        }
        this.redirectUri = redirectUri;
        if (extensionProperties != null) {
            this.extensions = extensionProperties;
        }
    }

    public Set<String> getResourceIds() {
        return resourceIds;
    }

    public void setResourceIds(Set<String> resourceIds) {
        this.resourceIds = resourceIds;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Collection<? extends GrantedAuthority> authorities) {
        this.authorities = authorities;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public TokenRequest getRefresh() {
        return refresh;
    }

    public void setRefresh(TokenRequest refresh) {
        this.refresh = refresh;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public Set<String> getResponseTypes() {
        return responseTypes;
    }

    public void setResponseTypes(Set<String> responseTypes) {
        this.responseTypes = responseTypes;
    }

    public Map<String, Serializable> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Serializable> extensions) {
        this.extensions = extensions;
    }
}
