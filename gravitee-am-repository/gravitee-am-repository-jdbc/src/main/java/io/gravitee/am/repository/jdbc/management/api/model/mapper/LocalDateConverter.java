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

import java.time.LocalDateTime;
import java.util.Date;

import static java.time.ZoneOffset.UTC;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LocalDateConverter extends DozerConverter<Date, LocalDateTime> {

    public LocalDateConverter() {
        super(Date.class, LocalDateTime.class);
    }

    @Override
    public LocalDateTime convertTo(Date date, LocalDateTime localDateTime) {
        LocalDateTime result = null;
        if (date != null) {
            result = LocalDateTime.ofInstant(date.toInstant(), UTC);
        }
        return result;
    }

    @Override
    public Date convertFrom(LocalDateTime localDateTime, Date date) {
        Date result = null;
        if (localDateTime != null) {
            result = Date.from(localDateTime.atZone(UTC).toInstant());
        }
        return result;
    }
}
