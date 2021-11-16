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
package io.gravitee.am.authdevice.notifier.api.model;

import io.vertx.reactivex.core.MultiMap;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ADCallbackContext {
    private final MultiMap headers;
    private final MultiMap params;

    public ADCallbackContext(MultiMap headers, MultiMap params) {
        this.headers = headers;
        this.params = params;
    }

    public MultiMap getParams() {
        return params;
    }

    public String getParam(String name) {
        return params.get(name);
    }

    public MultiMap getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public boolean containsParam(String name) {
        return this.params != null && this.params.contains(name);
    }

    public boolean containsHeader(String name) {
        return this.headers != null && this.headers.contains(name);
    }
}
