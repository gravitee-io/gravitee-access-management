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
import io.gravitee.am.model.AuthenticationDeviceNotifier;
import io.gravitee.am.service.model.NewAuthenticationDeviceNotifier;
import io.gravitee.am.service.model.UpdateAuthenticationDeviceNotifier;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AuthenticationDeviceNotifierService {

    Maybe<AuthenticationDeviceNotifier> findById(String id);

    Flowable<AuthenticationDeviceNotifier> findByDomain(String domain);

    Single<AuthenticationDeviceNotifier> create(String domain, NewAuthenticationDeviceNotifier notifier, User principal);

    Single<AuthenticationDeviceNotifier> update(String domain, String id, UpdateAuthenticationDeviceNotifier updateNotifier, User principal);

    Completable delete(String domain, String notifierId, User principal);

    default Single<AuthenticationDeviceNotifier> create(String domain, NewAuthenticationDeviceNotifier notifier) {
        return create(domain, notifier, null);
    }

    default Single<AuthenticationDeviceNotifier> update(String domain, String id, UpdateAuthenticationDeviceNotifier updateNotifier) {
        return update(domain, id, updateNotifier, null);
    }

    default Completable delete(String domain, String notifierId) {
        return delete(domain, notifierId, null);
    }
}
