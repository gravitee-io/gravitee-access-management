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
package io.gravitee.am.management.handlers.management.api.resources.model;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationType;

import java.util.Date;

public record FilteredApplication(
        String id,
        String name,
        String description,
        ApplicationType type,
        boolean enabled,
        boolean template,
        Date updatedAt) {

    public static FilteredApplication of(Application application) {
        return new FilteredApplication(
                application.getId(),
                application.getName(),
                application.getDescription(),
                application.getType(),
                application.isEnabled(),
                application.isTemplate(),
                application.getUpdatedAt()
        );
    }
}
