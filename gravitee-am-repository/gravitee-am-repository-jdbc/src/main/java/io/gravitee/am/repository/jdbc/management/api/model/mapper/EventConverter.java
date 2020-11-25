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
package io.gravitee.am.repository.jdbc.management.api.model.mapper;

import com.github.dozermapper.core.DozerConverter;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.jdbc.common.JSONMapper;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcEvent;

import java.util.HashMap;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EventConverter extends DozerConverter<Event, JdbcEvent> {
    private static final LocalDateConverter dateConverter = new LocalDateConverter();

    public EventConverter() {
        super(Event.class, JdbcEvent.class);
    }

    @Override
    public JdbcEvent convertTo(Event event, JdbcEvent jdbcEvent) {
        JdbcEvent result = null;
        if (event != null) {
            result = new JdbcEvent();
            result.setId(event.getId());
            result.setType(event.getType() == null ? null : event.getType().name());
            result.setPayload(JSONMapper.toJson(event.getPayload()));
            result.setCreatedAt(dateConverter.convertTo(event.getCreatedAt(), null));
            result.setUpdatedAt(dateConverter.convertTo(event.getUpdatedAt(), null));
        }
        return result;
    }

    @Override
    public Event convertFrom(JdbcEvent jdbcEvent, Event event) {
        Event result = null;
        if (jdbcEvent != null) {
            result = new Event();
            result.setId(jdbcEvent.getId());
            result.setType(jdbcEvent.getType() == null ? null : Type.valueOf(jdbcEvent.getType()));
            final HashMap payload = JSONMapper.toBean(jdbcEvent.getPayload(), HashMap.class);
            if (payload != null) {
                final String action = (String) payload.get("action");
                payload.put("action", action == null ? null : Action.valueOf(action));
                result.setPayload(new Payload(payload));
            }
            result.setCreatedAt(dateConverter.convertFrom(jdbcEvent.getCreatedAt(), null));
            result.setUpdatedAt(dateConverter.convertFrom(jdbcEvent.getUpdatedAt(), null));
        }
        return result;
    }
}
