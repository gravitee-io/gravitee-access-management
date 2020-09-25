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
package io.gravitee.am.repository.mongodb.common;

import io.gravitee.am.repository.management.api.search.FilterCriteria;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class FilterCriteriaParser {

    private FilterCriteriaParser() {}

    public static String parse(FilterCriteria criteria) {
        return parse(criteria, new StringBuilder());
    }

    private static String parse(FilterCriteria criteria, final StringBuilder builder) {
        if (criteria.getFilterComponents() != null) {
            builder.append("{");
            builder.append(convertOperator(criteria.getOperator()));
            builder.append(":");
            builder.append("[");
            int size = criteria.getFilterComponents().size();
            for (int i = 0; i < size; ++i) {
                parse(criteria.getFilterComponents().get(i), builder);
                if (i < size - 1) {
                    builder.append(",");
                }
            }
            builder.append("]");
        } else {
            String operator = convertOperator(criteria.getOperator());
            String filterName = convertFilterName(criteria.getFilterName());
            builder.append("{");
            builder.append("\"" + filterName + "\"");
            builder.append(":");
            builder.append("{");
            builder.append(operator);
            builder.append(":");
            if ("$exists".equals(operator)) {
                builder.append(true);
            } else {
                builder.append(convertFilterValue(criteria, filterName, criteria.getOperator()));
            }
            if ("$regex".equals(operator)) {
                builder.append(",$options:\"i\"");
            }
            builder.append("}");
        }
        builder.append("}");
        return builder.toString();
    }

    private static String convertOperator(String operator) {
        if (operator == null) {
            return null;
        }

        switch (operator) {
            case "or":
                return "$or";
            case "and":
                return "$and";
            case "eq":
                return "$eq";
            case "ne":
                return "$ne";
            case "gt":
                return "$gt";
            case "ge":
                return "$gte";
            case "lt":
                return "$lt";
            case "le":
                return "$lte";
            case "pr":
                return "$exists";
            case "co":
            case "sw":
            case "ew":
                return "$regex";
            default:
                return operator;
        }
    }

    private static String convertFilterName(String filterName) {
        if (filterName == null) {
            return null;
        }

        switch (filterName) {
            case "id":
                return "_id";
            case "userName":
                return "username";
            case "name.familyName":
                return "additionalInformation.family_name";
            case "name.givenName":
                return "additionalInformation.given_name";
            case "name.middleName":
                return "additionalInformation.middle_name";
            case "meta.created":
                return "createdAt";
            case "meta.lastModified":
                return "updatedAt";
            case "profileUrl":
                return "additionalInformation.profile";
            case "locale":
                return "additionalInformation.locale";
            case "timezone":
                return "additionalInformation.zoneinfo";
            case "active":
                return "enabled";
            case "emails.value":
                return "email";
            default:
                return filterName;
        }
    }

    private static String convertFilterValue(FilterCriteria criteria, String filterName, String operator) {
        String filterValue = criteria.getFilterValue();
        if (isDateInput(filterName)) {
            filterValue = "ISODate(\"" + filterValue + "\")";
            return filterValue;
        }
        if ("sw".equals(operator)) {
            filterValue = "^" + filterValue;
        }
        if ("ew".equals(operator)) {
            filterValue += "$";
        }
        if (criteria.isQuoteFilterValue()) {
            filterValue = "\"" + filterValue + "\"";
        }
        return filterValue;
    }

    private static boolean isDateInput(String filterName) {
        return "createdAt".equals(filterName) ||
                "updatedAt".equals(filterName);
    }
}
