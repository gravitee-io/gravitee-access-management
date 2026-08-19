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
package io.gravitee.am.common.oidc.command;

/**
 * Protocol constants of OpenID Provider Commands 1.0 (draft).
 * The spec is a draft: claim names and the response envelope may still change,
 * which is why every spec constant is isolated here.
 *
 * See https://openid.net/specs/openid-provider-commands-1_0.html
 *
 * @author GraviteeSource Team
 */
public interface CommandConstants {

    /**
     * JOSE header type of a command token.
     */
    String COMMAND_TOKEN_TYPE = "command+jwt";

    /**
     * Form parameter carrying the command token when POSTing to a command endpoint.
     */
    String COMMAND_TOKEN_PARAM = "command_token";

    /**
     * Claim naming the command to execute.
     */
    String COMMAND_CLAIM = "command";

    /**
     * Claim identifying the tenant (the security domain) the command applies to.
     */
    String TENANT_CLAIM = "tenant";

    /**
     * Error code returned by an RP when the account state is incompatible with the command.
     */
    String ERROR_INCOMPATIBLE_STATE = "incompatible_state";

    /**
     * Response member describing the account state at the RP.
     */
    String ACCOUNT_STATE = "account_state";

    /**
     * Account state returned by an RP that never provisioned the account: a benign no-op.
     */
    String ACCOUNT_STATE_UNKNOWN = "unknown";
}
