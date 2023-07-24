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

import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.common.service.Service;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface IdentityProviderManager extends Service<IdentityProviderManager> {

    Maybe<UserProvider> getUserProvider(String userProvider);

    Single<IdentityProvider> create(ReferenceType referenceType, String referenceId);

    Single<IdentityProvider> create(String domain);

    String createProviderConfiguration(String referenceId, NewIdentityProvider identityProvider);

    void setListener(InMemoryIdentityProviderListener listener);

    Completable loadIdentityProviders();

    Completable checkPluginDeployment(String type);
}
