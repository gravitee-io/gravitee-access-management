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
package io.gravitee.am.resource.api;

import io.gravitee.am.common.plugin.AmPluginProvider;
import io.gravitee.common.component.Lifecycle;
import io.gravitee.common.service.Service;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ResourceProvider extends Service<ResourceProvider>, AmPluginProvider {

    @Override
    default Lifecycle.State lifecycleState() {
        return Lifecycle.State.INITIALIZED;
    }

    @Override
    default ResourceProvider start() throws Exception {
        return this;
    }

    @Override
    default ResourceProvider stop() throws Exception {
        return this;
    }
}
