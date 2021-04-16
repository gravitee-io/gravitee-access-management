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
package io.gravitee.am.service;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.service.model.NewExtensionGrant;
import io.gravitee.am.service.model.UpdateExtensionGrant;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ExtensionGrantService {
    Maybe<ExtensionGrant> findById(String id);

    Single<List<ExtensionGrant>> findByDomain(String tokenGranter);

    Single<ExtensionGrant> create(String domain, NewExtensionGrant newExtensionGrant, User principal);

    Single<ExtensionGrant> update(String domain, String id, UpdateExtensionGrant updateExtensionGrant, User principal);

    Completable delete(String domain, String certificateId, User principal);

    default Single<ExtensionGrant> create(String domain, NewExtensionGrant newExtensionGrant) {
        return create(domain, newExtensionGrant, null);
    }

    default Single<ExtensionGrant> update(String domain, String id, UpdateExtensionGrant updateExtensionGrant) {
        return update(domain, id, updateExtensionGrant, null);
    }

    default Completable delete(String domain, String certificateId) {
        return delete(domain, certificateId, null);
    }
}
