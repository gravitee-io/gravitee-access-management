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
package io.gravitee.am.common.event;

/**
 * Event manager for AM gateway context
 *
 * We should unregister event listeners when a domain is removed
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
import io.gravitee.common.event.EventListener;

public interface EventManager extends io.gravitee.common.event.EventManager {

    <T extends Enum> void subscribeForEvents(EventListener<T, ?> eventListener, Class<T> events, String domain);

    <T extends Enum> void unsubscribeForEvents(EventListener<T, ?> eventListener, Class<T> events, String domain);

    <T extends Enum> void unsubscribeForCrossEvents(EventListener<T, ?> eventListener, Class<T> events, String domain);
}
