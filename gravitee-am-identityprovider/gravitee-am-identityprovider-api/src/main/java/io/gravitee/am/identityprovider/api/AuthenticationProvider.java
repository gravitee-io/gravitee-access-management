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
package io.gravitee.am.identityprovider.api;

import io.gravitee.common.component.Lifecycle;
import io.gravitee.common.service.Service;
import io.reactivex.rxjava3.core.Maybe;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AuthenticationProvider extends Service<AuthenticationProvider> {

    String ACTUAL_USERNAME = "actual_username";

    Maybe<User> loadUserByUsername(Authentication authentication);

    Maybe<User> loadUserByUsername(String username);

    default Maybe<User> loadPreAuthenticatedUser(Authentication authentication) {
        io.gravitee.am.model.User user = (io.gravitee.am.model.User) authentication.getPrincipal();
        return loadUserByUsername(user.getUsername());
    }

    default Metadata metadata(String idpUrl) {
        return null;
    }

    default Lifecycle.State lifecycleState() {
        return Lifecycle.State.INITIALIZED;
    }

    default AuthenticationProvider start() throws Exception {
        return this;
    }

    default AuthenticationProvider stop() throws Exception {
        return this;
    }
}
