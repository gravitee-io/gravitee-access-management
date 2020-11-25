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

import java.util.HashMap;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ScimUserSearch {
    private StringBuilder queryBuilder;
    private String selectQuery;
    private String countQuery;
    private Map<String, Object> binding = new HashMap<>();

    public void setSelectQuery(String selectQuery) {
        this.selectQuery = selectQuery;
    }

    public void setCountQuery(String countQuery) {
        this.countQuery = countQuery;
    }

    public String getSelectQuery() {
        return selectQuery;
    }

    public String getCountQuery() {
        return countQuery;
    }

    public String addBinding(String name, Object value) {
        String bName = name;
        if (this.binding.containsKey(name)) {
            // allow to provide multiple time a value for a given field (ex: created_at > x && created_at < y)
            long copy = this.binding.keySet().stream().filter(k -> k.startsWith(name + "_c")).count();
            bName = name+"_c"+copy;
        }
        this.binding.put(bName, value);
        return ":"+bName;
    }

    public Map<String, Object> getBinding() {
        return binding;
    }

    public StringBuilder getQueryBuilder() {
        return queryBuilder;
    }

    public StringBuilder updateBuilder(StringBuilder builder) {
        this.queryBuilder = builder;
        return this.queryBuilder;
    }

    public void buildQueries(String limitClause) {
        this.countQuery = "SELECT count(id) " + this.queryBuilder.toString();
        this.selectQuery = "SELECT * " + this.queryBuilder.toString() + limitClause;
    }
}
