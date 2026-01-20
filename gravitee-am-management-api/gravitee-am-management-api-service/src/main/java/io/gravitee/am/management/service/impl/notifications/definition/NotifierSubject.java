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
package io.gravitee.am.management.service.impl.notifications.definition;


import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;

import java.util.Map;

public interface NotifierSubject {
    String NOTIFIER_DATA_USER = "user";
    String NOTIFIER_DATA_DOMAIN = "domain";
    String NOTIFIER_DATA_CERTIFICATE = "certificate";
    String NOTIFIER_DATA_CLIENT_SECRET = "clientSecret";
    String NOTIFIER_DATA_APPLICATION = "application";
    String NOTIFIER_DATA_PROTECTED_RESOURCE = "protectedResource";

    String getResourceType();
    String getResourceId();

    Domain getDomain();
    User getUser();

    Map<String, Object> getData();
    default Map<String, Object> getMetadata() {
        return Map.of();
    }
}
