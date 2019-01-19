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
package io.gravitee.am.identityprovider.mongo;

import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.mongo.authentication.MongoAuthenticationProvider;
import io.gravitee.am.identityprovider.mongo.user.MongoUserProvider;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoIdentityProvider implements IdentityProvider {
    public Class<? extends IdentityProviderConfiguration> configuration() {
        return MongoIdentityProviderConfiguration.class;
    }

    public Class<? extends AuthenticationProvider> authenticationProvider() {
        return MongoAuthenticationProvider.class;
    }

    public Class<? extends UserProvider> userProvider() {
        return MongoUserProvider.class;
    }

    public Class<? extends IdentityProviderMapper> mapper() {
        return MongoIdentityProviderMapper.class;
    }

    public Class<? extends MongoIdentityProviderRoleMapper> roleMapper() {
        return MongoIdentityProviderRoleMapper.class;
    }
}
