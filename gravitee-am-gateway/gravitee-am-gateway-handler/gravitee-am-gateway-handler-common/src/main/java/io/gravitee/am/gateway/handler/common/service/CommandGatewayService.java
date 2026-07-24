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

import io.gravitee.am.common.event.CommandEvent;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.Service;

/**
 * Listens for COMMAND sync events on a security domain and stages the command
 * dispatch job. Every gateway node receives the broadcast event; the idempotent
 * staging insert (keyed by the command id) guarantees a single dispatch job that
 * the {@link io.gravitee.am.gateway.handler.common.command.CommandStagingProcessor}
 * of the lease-holding node then processes.
 *
 * @author GraviteeSource Team
 */
public interface CommandGatewayService extends EventListener<CommandEvent, Payload>, Service {
}
