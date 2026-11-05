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
package io.gravitee.am.service.model;

import io.gravitee.am.model.IdentityProvider;

/**
 * An identity provider returned by a listing.
 *
 * @param identityProvider the identity provider, whose configuration is always emptied
 * @param configurationReadable false when the stored configuration could not be decrypted, so the identity provider cannot be used
 * @author GraviteeSource Team
 */
public record ListedIdentityProvider(IdentityProvider identityProvider, boolean configurationReadable) {
}
