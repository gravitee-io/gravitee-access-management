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
package io.gravitee.am.gateway.handler.aauth.service.bootstrap;

/**
 * Thrown when a bootstrap request is not found in the repository.
 *
 * @author GraviteeSource Team
 */
public class BootstrapRequestNotFoundException extends RuntimeException {
    public BootstrapRequestNotFoundException(String id) {
        super(String.format("Bootstrap request %s not found", id));
    }
}
