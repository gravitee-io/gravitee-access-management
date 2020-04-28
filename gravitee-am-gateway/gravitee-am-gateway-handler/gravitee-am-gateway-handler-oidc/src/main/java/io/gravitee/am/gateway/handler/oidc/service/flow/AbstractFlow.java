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
package io.gravitee.am.gateway.handler.oidc.service.flow;

import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.response.AuthorizationResponse;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Single;

import java.util.List;
import java.util.Objects;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractFlow implements Flow {

    private final List<String> responseTypes;

    public AbstractFlow(final List<String> responseTypes) {
        Objects.requireNonNull(responseTypes);
        this.responseTypes = responseTypes;
    }

    @Override
    public boolean handle(String responseType) {
        return responseTypes.contains(responseType);
    }

    @Override
    public Single<AuthorizationResponse> run(AuthorizationRequest authorizationRequest, Client client, User endUser) {
        return prepareResponse(authorizationRequest, client, endUser);
    }

    protected abstract Single<AuthorizationResponse> prepareResponse(AuthorizationRequest authorizationRequest, Client client, User endUser);
}
