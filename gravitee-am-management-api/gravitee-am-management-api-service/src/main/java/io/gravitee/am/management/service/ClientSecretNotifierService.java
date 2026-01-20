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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.application.ClientSecret;
import io.reactivex.rxjava3.core.Completable;

public interface ClientSecretNotifierService {

    Completable registerClientSecretExpiration(Application application, ClientSecret clientSecret);

    Completable registerClientSecretExpiration(ProtectedResource protectedResource, ClientSecret clientSecret);

    Completable unregisterClientSecretExpiration(String clientSecretId);

    Completable deleteClientSecretExpirationAcknowledgement(String clientSecretId);
}
