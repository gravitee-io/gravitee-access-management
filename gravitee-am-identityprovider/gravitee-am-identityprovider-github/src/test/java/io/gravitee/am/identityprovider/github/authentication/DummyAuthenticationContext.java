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

import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DummyAuthenticationContext implements AuthenticationContext {

    private final Map<String, Object> attributes;
    private final Request request;

    DummyAuthenticationContext(Map<String, Object> attributes, Request request) {
        this.attributes = attributes;
        this.request = request;
    }

    @Override
    public Request request() {
        return request;
    }

    @Override
    public Response response() {
        throw new IllegalStateException();
    }

    @Override
    public <T> T getComponent(Class<T> componentClass) {
        throw new IllegalStateException();
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public TemplateEngine getTemplateEngine() {
        throw new IllegalStateException();
    }
}
