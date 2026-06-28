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
package io.gravitee.am.authdevice.notifier.api;

import java.util.Optional;

/**
 * Opt-in role for authentication-device notifiers whose identity is established by a
 * registered AM identity provider. Core reads only the IdP id; it resolves the connection
 * itself. Notifiers that do not federate (e.g. the stock HTTP notifier) do not implement this.
 */
@FunctionalInterface
public interface IdentityProviderDependent {
    Optional<String> getIdentityProviderId();
}
