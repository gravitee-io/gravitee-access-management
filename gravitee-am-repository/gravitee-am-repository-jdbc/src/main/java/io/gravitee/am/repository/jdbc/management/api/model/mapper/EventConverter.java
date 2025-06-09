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
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.token.RevokeToken;
import io.gravitee.am.model.token.RevokeType;
import io.gravitee.am.repository.jdbc.provider.common.JSONMapper;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static java.util.Optional.ofNullable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EventConverter extends DozerConverter<Event, JdbcEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventConverter.class);
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
            result.setDataPlaneId(event.getDataPlaneId());
            result.setEnvironmentId(event.getEnvironmentId());
        }
        return result;
    }

    @Override
    public Event convertFrom(JdbcEvent jdbcEvent, Event event) {
        Event result = null;
        if (jdbcEvent != null) {
            result = new Event();
            result.setId(jdbcEvent.getId());
            final var payload = JSONMapper.toBean(jdbcEvent.getPayload(), HashMap.class);
            if (payload != null) {
                final String action = (String) payload.get("action");
                payload.put("action", action == null ? null : Action.valueOf(action));
                ofNullable(payload.get(Payload.REVOKE_TOKEN_DEFINITION))
                        .filter(obj -> obj instanceof Map)
                        .ifPresent(obj -> payload.put(Payload.REVOKE_TOKEN_DEFINITION, toRevokeToken((Map) obj)));
                result.setPayload(new Payload(payload));
            }
            result.setCreatedAt(dateConverter.convertFrom(jdbcEvent.getCreatedAt(), null));
            result.setUpdatedAt(dateConverter.convertFrom(jdbcEvent.getUpdatedAt(), null));
            try {
                result.setType(jdbcEvent.getType() == null ? null : Type.valueOf(jdbcEvent.getType()));
            } catch (IllegalArgumentException e) {
                LOGGER.info("Invalid event type '{}', the event will be ignored by synchronization process.", jdbcEvent.getType());
                result.setType(Type.UNKNOWN);
            }
            result.setDataPlaneId(jdbcEvent.getDataPlaneId());
            result.setEnvironmentId(jdbcEvent.getEnvironmentId());
        }
        return result;
    }


    private RevokeToken toRevokeToken(Map revokeToken) {
        if (revokeToken == null) {
            return null;
        }

        RevokeToken document = new RevokeToken();
        document.setRevokeType(RevokeType.valueOf((String) revokeToken.get("revokeType")));
        document.setDomainId((String) revokeToken.get("domainId"));
        document.setClientId((String) revokeToken.get("clientId"));
        document.setUserId(toUserId((Map) revokeToken.get("userId")));

        return document;
    }

    private UserId toUserId(Map userId) {
        if (userId == null) {
            return null;
        }
        return new UserId((String) userId.get("id"), (String) userId.get("externalId"), (String) userId.get("source"));
    }
}
