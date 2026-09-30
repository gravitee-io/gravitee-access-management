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

import io.gravitee.am.model.Reporter;
import io.gravitee.am.service.ReporterService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ReporterServiceProxy extends ReporterService {

    /**
     * Returns a copy of the reporter with its sensitive configuration values masked.
     */
    Single<Reporter> filterSensitiveData(Reporter reporter);

    /**
     * Errors with {@link io.gravitee.am.service.exception.InvalidParameterException} when a sensitive value in
     * the reporter's configuration is the mask.
     */
    Completable rejectMaskedSensitiveValues(Reporter reporter);
}
