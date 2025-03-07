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

package io.gravitee.am.gateway.handler.common.service;

import io.gravitee.am.common.event.RevokeTokenEvent;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.token.RevokeToken;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.Service;
import io.reactivex.rxjava3.core.Completable;

public interface RevokeTokenGatewayService extends EventListener<RevokeTokenEvent, Payload>, Service {

    default Completable deleteByUser(User user) {
        return deleteByUser(user, true);
    }

    Completable deleteByUser(User user, boolean needAudit);

    /**
     * remove access & refresh tokens based on the RokenToken content.
     * This method do not generate audits as it used as a sub process
     * of a higher level action which will trace the action in an audit.
     *
     * @param domain
     * @param revokeTokenDescription
     * @return
     */
    Completable process(Domain domain, RevokeToken revokeTokenDescription);

}
