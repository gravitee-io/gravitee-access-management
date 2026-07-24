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

import io.gravitee.am.common.oidc.command.Command;
import io.gravitee.am.model.Domain;
import io.reactivex.rxjava3.core.Completable;

/**
 * Schedules OpenID Provider Commands for dispatch by the gateway.
 * The Management API only persists a COMMAND event on the sync pipeline: command
 * tokens are minted and POSTed by the gateway, which is the party with data-plane
 * repository access and network reachability to the RP command endpoints.
 *
 * @author GraviteeSource Team
 */
public interface CommandManagementService {

    /**
     * Schedule a command targeting a domain user, to be dispatched to every
     * application of the domain that registered a command_endpoint.
     *
     * @param domain the security domain
     * @param command the command to dispatch
     * @param userId the AM internal id of the user the command applies to
     * @param principal the administrator triggering the command (for audit)
     * @return completable completing once the event is persisted
     */
    Completable sendCommand(Domain domain, Command command, String userId, io.gravitee.am.identityprovider.api.User principal);
}
