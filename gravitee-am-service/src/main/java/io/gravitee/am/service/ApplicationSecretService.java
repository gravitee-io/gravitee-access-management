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
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.service.model.NewClientSecret;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

public interface ApplicationSecretService {

    // the widths of the JDBC columns, applied on every repository so that a definition is portable
    int KEY_MAX_LENGTH = 64;
    int PLAIN_VALUE_MAX_LENGTH = 374;

    Flowable<ClientSecret> findAllByApplication(Application application);

    Single<Application> renew(Domain domain, Application application, String id, User principal);

    Completable delete(Domain domain, Application application, String id, User principal);

    Single<ClientSecret> create(Domain domain, Application application, NewClientSecret clientSecret, User principal);

    Maybe<ClientSecret> findById(String applicationId, String id);

    /**
     * Validates adding {@code desired} with {@code rawSecret} to the application without persisting.
     *
     * @return the secret that would be added, or the stored one when it already exists with this value
     */
    Single<ClientSecret> validateApply(Domain domain, Application application, ClientSecret desired, String rawSecret);

    /**
     * Adds {@code desired} with {@code rawSecret}, or keeps it when it already exists with the same value.
     * {@code desired} carries the secret identity ({@code id}, {@code name}, {@code automationKey}, {@code managedBy});
     * a secret that is not managed by the Automation API can only be kept, never created.
     * The application is re-read, and the write retried, on a concurrent modification.
     *
     * @return the secret, without its value
     */
    Single<ClientSecret> apply(Domain domain, Application application, ClientSecret desired, String rawSecret, User principal);

    /**
     * Removes the secret; unlike {@link #delete}, removing the last secret is allowed. A missing secret completes.
     * The application is re-read, and the write retried, on a concurrent modification.
     */
    Completable remove(Domain domain, Application application, String secretId, User principal);
}
