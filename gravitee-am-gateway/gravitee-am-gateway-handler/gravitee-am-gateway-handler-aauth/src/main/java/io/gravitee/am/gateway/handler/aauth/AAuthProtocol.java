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
package io.gravitee.am.gateway.handler.aauth;

import io.gravitee.am.gateway.handler.aauth.spring.AAuthConfiguration;
import io.gravitee.am.gateway.handler.api.Protocol;

/**
 * AAUTH Protocol plugin entry point.
 *
 * @author GraviteeSource Team
 */
public class AAuthProtocol extends Protocol<AAuthConfiguration, AAuthProvider> {

    @Override
    public Class<AAuthConfiguration> configuration() {
        return AAuthConfiguration.class;
    }

    @Override
    public Class<AAuthProvider> provider() {
        return AAuthProvider.class;
    }
}
