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
package io.gravitee.am.management.service.exception;

import io.gravitee.am.service.exception.AbstractNotFoundException;

/**
 * @author GraviteeSource Team
 */
public class AuthorizationEnginePluginNotFoundException extends AbstractNotFoundException {

    private final String plugin;

    public AuthorizationEnginePluginNotFoundException(String plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getMessage() {
        return "Authorization Engine plugin [" + plugin + "] cannot be found.";
    }
}
