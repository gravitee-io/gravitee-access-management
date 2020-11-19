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
package io.gravitee.am.reporter.jdbc.dialect;

import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SearchQuery {
    private String query;
    private String count;
    private Map<String, Object> bindings;

    public SearchQuery(String query, String count, Map<String, Object> bindings) {
        this.query = query;
        this.count = count;
        this.bindings = bindings;
    }

    public String getQuery() {
        return query;
    }

    public String getCount() {
        return count;
    }

    public Map<String, Object> getBindings() {
        return bindings;
    }
}
