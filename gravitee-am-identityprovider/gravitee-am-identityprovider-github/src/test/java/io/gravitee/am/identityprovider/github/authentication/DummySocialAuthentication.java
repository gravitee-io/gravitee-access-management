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

package io.gravitee.am.identityprovider.github.authentication;

import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.DummyAuthenticationContext;
import io.gravitee.am.identityprovider.api.DummyRequest;

import java.util.List;
import java.util.Map;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DummySocialAuthentication implements Authentication {

    private final DummyRequest request;
    private final Map<String, Object> attributes;
    private DummyAuthenticationContext dummyAuthenticationContext;

    public DummySocialAuthentication(Map<String, List<String>> parameters, Map<String, Object> attributes) {
        this.request = new DummyRequest();
        this.request.setParameters(parameters);
        this.attributes = attributes;
        dummyAuthenticationContext = new DummyAuthenticationContext(this.attributes, request);
    }

    @Override
    public Object getCredentials() {
        return "__social__";
    }

    @Override
    public Object getPrincipal() {
        return "__social__";
    }

    @Override
    public AuthenticationContext getContext() {
        return dummyAuthenticationContext;
    }
}
