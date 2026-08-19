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
package io.gravitee.am.model.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Request to dispatch an OpenID Provider Command to the applications of a security
 * domain that registered a command_endpoint. Carried as flat values in the sync
 * {@link io.gravitee.am.model.common.event.Payload} from the Management API to the
 * gateway; the signed command tokens are only minted at dispatch time so their
 * iat/exp/jti are fresh, including on retries.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandRequest {

    /**
     * Unique identifier of the command occurrence, generated at trigger time.
     * Used as the staging key so N gateway nodes racing on the same sync event
     * stage exactly one dispatch job.
     */
    private String id;

    /**
     * The command to execute, one of {@link io.gravitee.am.common.oidc.command.Command} values.
     */
    private String command;

    /**
     * The AM internal id of the user the command applies to.
     */
    private String userId;

    private String domainId;

    /**
     * The administrator who triggered the command (for audit purpose).
     */
    private String principalId;

    private String principalUsername;
}
