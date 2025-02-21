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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.token.RevokeToken;
import io.reactivex.rxjava3.core.Completable;

public interface RevokeTokenManagementService {

    Completable deleteByUser(Domain domain, User user);

    Completable deleteByApplication(Domain domain, Application application);

    /**
     * remove access & refresh tokens based on the RevokeToken content.
     * This method do not generate audits as it used as a sub process
     * of a higher level action which will trace the action in an audit.
     *
     * @parm domain
     * @param revokeTokenDescription
     * @return
     */
    Completable sendProcessRequest(Domain domain, RevokeToken revokeTokenDescription);
}
