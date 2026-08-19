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

import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Flowable;

/**
 * Resolves the targets of an OpenID Provider Command blast: every client of the
 * domain whose own metadata registers a command_endpoint. A false positive costs
 * one POST answered by the spec-defined benign 409; a false negative would be a
 * security failure.
 *
 * @author GraviteeSource Team
 */
public interface CommandTargetResolver {

    Flowable<Client> resolveTargets();
}
