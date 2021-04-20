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
package io.gravitee.am.identityprovider.http.configuration;

import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;
import io.gravitee.common.http.HttpHeader;
import io.gravitee.common.http.HttpMethod;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HttpIdentityProviderConfiguration implements IdentityProviderConfiguration {

    /* ----- Authentication Resource ----- */
    private HttpResourceConfiguration authenticationResource;

    /* ----- Users Resource ----- */
    private HttpUsersResourceConfiguration usersResource;

    /* ----- HTTP client properties ----- */
    private Integer connectTimeout = 10000;
    private Integer maxPoolSize = 200;

    @Override
    public boolean userProvider() {
        return usersResource.isEnabled();
    }

    public HttpResourceConfiguration getAuthenticationResource() {
        return authenticationResource;
    }

    public void setAuthenticationResource(HttpResourceConfiguration authenticationResource) {
        this.authenticationResource = authenticationResource;
    }

    public HttpUsersResourceConfiguration getUsersResource() {
        return usersResource;
    }

    public void setUsersResource(HttpUsersResourceConfiguration usersResource) {
        this.usersResource = usersResource;
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }
}
