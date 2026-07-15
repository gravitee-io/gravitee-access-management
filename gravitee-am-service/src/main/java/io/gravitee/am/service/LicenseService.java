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

import io.gravitee.am.model.License;
import io.gravitee.am.model.ReferenceType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author GraviteeSource Team
 */
public interface LicenseService {

    Flowable<License> findAll();

    Maybe<License> findByReference(ReferenceType referenceType, String referenceId);

    Single<License> createOrUpdate(ReferenceType referenceType, String referenceId, String license);

    Completable delete(ReferenceType referenceType, String referenceId);

    /**
     * @throws io.gravitee.am.service.exception.InvalidLicenseException if the license is not a non-blank base64-encoded value
     */
    void validate(String license);
}
