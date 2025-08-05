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
package io.gravitee.am.model.common.event;

import io.gravitee.am.common.event.Type;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@EqualsAndHashCode
@Slf4j
@Getter
public class EventKey {
    private final Type type;
    private final String key;

    public EventKey(Event event) {
        switch (event.getType()) {
            case REPORTER -> {
                this.type = Type.REPORTER;
                this.key = reporterKey(event);
            }
            default -> {
                this.type = event.getType();
                this.key = defaultKey(event);
            }
        }
    }

    private String defaultKey(Event event) {
        return event.getPayload().getId();
    }

    private String reporterKey(Event event) {
        try {
            return Optional.ofNullable(event.getPayload().get("childReporterReference"))
                    .map(Map.class::cast)
                    .map(ref -> event.getPayload().getId() + "/" + ref.get("id"))
                    .orElseGet(() -> defaultKey(event));
        } catch (Exception e) {
            log.debug("error on key evaluation", e);
            return defaultKey(event);
        }
    }
}
