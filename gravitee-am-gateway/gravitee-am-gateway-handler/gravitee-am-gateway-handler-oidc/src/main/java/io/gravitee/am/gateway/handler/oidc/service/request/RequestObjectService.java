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
package io.gravitee.am.gateway.handler.oidc.service.request;

import com.nimbusds.jwt.JWT;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Teams
 */
public interface RequestObjectService {

    /**
     * The URN prefix used by AS when storing Request Object
     */
    String RESOURCE_OBJECT_URN_PREFIX = "urn:ros:";

    /**
     * Validate encryption, signature and read the content of the JWT token.
     *
     * @param request
     * @param client
     * @param encRequired true if the request object has to be encrypted (JWE)
     * @return
     */
    Single<JWT> readRequestObject(String request, Client client, boolean encRequired);

    /**
     * Validate encryption, signature and read the content of the JWT token from the URI.
     *
     * @param requestUri
     * @param client
     * @return
     */
    Single<JWT> readRequestObjectFromURI(String requestUri, Client client);

    /**
     * Register a request object for a given Client
     * @return
     */
    Single<RequestObjectRegistrationResponse> registerRequestObject(RequestObjectRegistrationRequest request, Client client);
}
