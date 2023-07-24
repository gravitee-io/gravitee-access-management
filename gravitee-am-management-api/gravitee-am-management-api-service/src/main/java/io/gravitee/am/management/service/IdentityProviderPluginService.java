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
package io.gravitee.am.management.service;

import io.gravitee.am.service.model.plugin.IdentityProviderPlugin;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface IdentityProviderPluginService extends PluginService {

    String EXPAND_DISPLAY_NAME = "displayName";
    String EXPAND_ICON = "icon";
    String EXPAND_LABELS = "labels";

    Single<List<IdentityProviderPlugin>> findAll(List<String> expand);

    Single<List<IdentityProviderPlugin>> findAll(Boolean external);

    Single<List<IdentityProviderPlugin>> findAll(Boolean external, List<String> expand);

    Maybe<IdentityProviderPlugin> findById(String identityProviderPlugin);

    Maybe<String> getSchema(String identityProviderPlugin);

    Maybe<String> getIcon(String identityProviderPlugin);
}
