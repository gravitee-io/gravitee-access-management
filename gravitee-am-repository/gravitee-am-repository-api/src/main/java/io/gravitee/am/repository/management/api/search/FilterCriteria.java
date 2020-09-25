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
package io.gravitee.am.repository.management.api.search;

import io.gravitee.am.common.scim.Schema;
import io.gravitee.am.common.scim.filter.Filter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FilterCriteria {

    private String operator;
    private String filterName;
    private String filterValue;
    private boolean quoteFilterValue;
    private List<FilterCriteria> filterComponents;

    public FilterCriteria() { }

    public FilterCriteria(String operator, String filterName, String filterValue, boolean quoteFilterValue, List<FilterCriteria> filterComponents) {
        this.operator = operator;
        this.filterName = filterName;
        this.filterValue = filterValue;
        this.quoteFilterValue = quoteFilterValue;
        this.filterComponents = filterComponents;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getFilterName() {
        return filterName;
    }

    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }

    public String getFilterValue() {
        return filterValue;
    }

    public void setFilterValue(String filterValue) {
        this.filterValue = filterValue;
    }

    public boolean isQuoteFilterValue() {
        return quoteFilterValue;
    }

    public void setQuoteFilterValue(boolean quoteFilterValue) {
        this.quoteFilterValue = quoteFilterValue;
    }

    public List<FilterCriteria> getFilterComponents() {
        return filterComponents;
    }

    public void setFilterComponents(List<FilterCriteria> filterComponents) {
        this.filterComponents = filterComponents;
    }

    public static FilterCriteria convert(Filter scimFilter) {
        if (scimFilter == null) {
            return null;
        }
        String filterAttribute = scimFilter.getFilterAttribute() != null ?
                scimFilter.getFilterAttribute()
                        .toString()
                        .replace(Schema.SCHEMA_URI_USER + ":", "")
                        .replace(Schema.SCHEMA_URI_GROUP + ":", "") : null;
        FilterCriteria filterCriteria = new FilterCriteria(
                scimFilter.getOperator().getValue(),
                filterAttribute,
                scimFilter.getFilterValue(),
                scimFilter.isQuoteFilterValue(),
                scimFilter.getFilterComponents() != null ? scimFilter.getFilterComponents().stream().map(FilterCriteria::convert).collect(Collectors.toList()) : null);
        return filterCriteria;
    }

    @Override
    public String toString() {
        return "{\"_class\":\"FilterCriteria\", " +
                "\"operator\":" + (operator == null ? "null" : "\"" + operator + "\"") + ", " +
                "\"filterName\":" + (filterName == null ? "null" : "\"" + filterName + "\"") + ", " +
                "\"filterValue\":" + (filterValue == null ? "null" : "\"" + filterValue + "\"") + ", " +
                "\"quoteFilterValue\":\"" + quoteFilterValue + "\"" + ", " +
                "\"filterComponents\":" + (filterComponents == null ? "null" : Arrays.toString(filterComponents.toArray())) +
                "}";
    }
}
