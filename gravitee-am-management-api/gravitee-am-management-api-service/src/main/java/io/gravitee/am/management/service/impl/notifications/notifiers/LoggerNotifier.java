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
package io.gravitee.am.management.service.impl.notifications.notifiers;

import io.gravitee.notifier.api.Notification;
import io.gravitee.notifier.api.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;


@Component
public class LoggerNotifier implements Notifier {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    public static final String NOTIFIER_DATA_MSG = "msg";

    @Override
    public CompletableFuture<Void> send(Notification notification, Map<String, Object> map) {
        final String message = ((String) map.get(NOTIFIER_DATA_MSG));
        logger.warn(message);

        return CompletableFuture.completedFuture(null);
    }
}
