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
package io.gravitee.am.gateway.handler.oauth2.service.validation;

import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.reactivex.rxjava3.core.Completable;

/**
 * Contract for validating RFC 8707 resource indicators in OAuth2 requests.
 */
public interface ResourceValidationService {

    /**
     * Validate resource parameters for any OAuth2 request (authorization or token).
     *
     * @param request the request (resources read from request.getResources())
     * @return a Completable that completes on success or errors with InvalidResourceException
     */
    Completable validate(OAuth2Request request);
}


