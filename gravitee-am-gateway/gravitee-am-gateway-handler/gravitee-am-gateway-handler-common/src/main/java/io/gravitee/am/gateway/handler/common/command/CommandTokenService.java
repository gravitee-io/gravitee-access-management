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
package io.gravitee.am.gateway.handler.common.command;

import io.gravitee.am.model.command.CommandStaging;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;

/**
 * Mints signed command tokens (JOSE header <code>typ: command+jwt</code>).
 * A fresh token is minted for every delivery attempt so iat/exp/jti stay valid on retries.
 *
 * @author GraviteeSource Team
 */
public interface CommandTokenService {

    /**
     * @param commandStaging the command dispatch job
     * @param client the target application; its command_endpoint is the token audience
     * @return the signed command token
     */
    Single<String> mintToken(CommandStaging commandStaging, Client client);
}
