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
package io.gravitee.am.gateway.handler.oauth2.approval;

import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.reactivex.Single;

/**
 * Approval service to obtain an authorization decision by asking the resource owner or by establishing approval via other means.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ApprovalService {

    Single<AuthorizationRequest> checkApproval(AuthorizationRequest authorizationRequest, Client client, User user);

    Single<AuthorizationRequest> saveApproval(AuthorizationRequest authorizationRequest, Client client, User user, io.gravitee.am.identityprovider.api.User principal);

    default Single<AuthorizationRequest> saveApproval(AuthorizationRequest authorizationRequest, Client client, User user) {
        return saveApproval(authorizationRequest, client, user, null);
    }
}
