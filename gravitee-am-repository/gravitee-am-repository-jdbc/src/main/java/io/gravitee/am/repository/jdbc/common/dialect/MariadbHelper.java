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
package io.gravitee.am.repository.jdbc.common.dialect;

import org.springframework.data.r2dbc.dialect.R2dbcDialect;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MariadbHelper extends MySqlHelper {
    public MariadbHelper(R2dbcDialect dialect, String collation) {
        super(dialect, collation == null ? "utf8mb4_bin" : collation);
    }

    @Override
    protected void loadJdbcDriver() throws Exception {
        Class.forName("org.mariadb.jdbc.Driver");
    }

    @Override
    public String buildAuthorizationCodeDeleteAndReturnQuery() {
        throw new UnsupportedOperationException("MariaDB doesn't support returning deleted");
    }

    @Override
    public String recursiveTokenDeleteQuery(String whereClause) {
        return """
                DELETE FROM tokens WHERE token IN (
                WITH RECURSIVE token_tree AS (
                SELECT token FROM tokens WHERE %s
                UNION ALL
                SELECT t.token FROM tokens t
                JOIN token_tree tt ON (t.parent_subject_jti = tt.token OR t.parent_actor_jti = tt.token)
                ) SELECT token FROM token_tree
                )
                """.formatted(whereClause);
    }
}
