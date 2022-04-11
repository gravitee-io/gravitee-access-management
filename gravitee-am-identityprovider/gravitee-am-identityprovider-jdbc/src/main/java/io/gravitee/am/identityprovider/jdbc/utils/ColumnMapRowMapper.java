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
package io.gravitee.am.identityprovider.jdbc.utils;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class ColumnMapRowMapper {

    public static Map<String, Object> mapRow(Row row, RowMetadata rowMetadata) {
        Map<String, Object> claims = new HashMap<>(rowMetadata.getColumnNames()
                .stream()
                .filter(c -> row.get(c) != null)
                .collect(Collectors.toMap(c -> c, c -> row.get(c))));
        return claims;
    }
}
