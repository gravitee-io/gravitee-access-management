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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class FilterCriteriaParser {

    private static final int MAX_SIZE = 64;
    private static final List<String> nameSpecialCharsList = new ArrayList<>() {
        {
            add("'");
            add("\"");
            add("\\");
            add(";");
            add("{");
            add("}");
            add("$");
        }
    };

    private static final List<String> valueSpecialCharsList = new ArrayList<>() {
        {
            add("\"");
            add("\\");
            add(";");
            add("{");
            add("}");
            add("$");
            add("^");
        }
    };
    public static final String FOUND_IN_THE_THE_SEARCH_QUERY = "] found in the the search query";
    private static final List<String> REGEX_OPERATORS = new ArrayList<>(){
        {
            add("co");
            add("sw");
            add("ew");
        }
    };
    public static final List<String> ALLOWED_REGEX_CHARACTERS = new ArrayList<>(){
        {
            add(".");
            add("*");
            add("+");
            add("?");
            add("[");
            add("]");
            add("|");
            add("(");
            add(")");
        }
        };

    private final boolean regexCaseInsensitive;

    public FilterCriteriaParser() {
        this(false);
    }

    public FilterCriteriaParser(boolean regexCaseInsensitive) {
        this.regexCaseInsensitive = regexCaseInsensitive;
    }

    public String parse(FilterCriteria criteria) {
        return parse(criteria, new StringBuilder());
    }

    private String parse(FilterCriteria criteria, final StringBuilder builder) {
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
            if ("pr".equals(criteria.getOperator())) {
                builder.append("null");
            } else {
                builder.append(convertFilterValue(criteria, filterName, criteria.getOperator()));
            }
            if (regexCaseInsensitive && "$regex".equals(operator)) {
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

        return switch (operator) {
            case "or" -> "$or";
            case "and" -> "$and";
            case "eq" -> "$eq";
            case "ne", "pr" -> "$ne";
            case "gt" -> "$gt";
            case "ge" -> "$gte";
            case "lt" -> "$lt";
            case "le" -> "$lte";
            case "co", "sw", "ew" -> "$regex";
            default -> throw new IllegalArgumentException("Invalid operator [" + operator + "] found in the search query");
        };
    }

    private static String convertFilterName(String filterName) {
        if (filterName == null) {
            return null;
        }

        if (nameSpecialCharsList.stream().anyMatch(filterName::contains)) {
            throw new IllegalArgumentException("Invalid filter name [" + filterName + FOUND_IN_THE_THE_SEARCH_QUERY);
        }

        if (filterName.length() > MAX_SIZE) {
            throw new IllegalArgumentException("Invalid filter name [" + filterName + FOUND_IN_THE_THE_SEARCH_QUERY);
        }

        return switch (filterName) {
            case "id" -> "_id";
            case "userName" -> "username";
            case "name.familyName" -> "additionalInformation.family_name";
            case "name.givenName" -> "additionalInformation.given_name";
            case "name.middleName" -> "additionalInformation.middle_name";
            case "meta.created" -> "createdAt";
            case "meta.lastModified" -> "updatedAt";
            case "profileUrl" -> "additionalInformation.profile";
            case "locale" -> "additionalInformation.locale";
            case "timezone" -> "additionalInformation.zoneinfo";
            case "active" -> "enabled";
            case "emails.value" -> "email";
            case "meta.loggedAt" -> "loggedAt";
            case "meta.lastLoginWithCredentials" -> "lastLoginWithCredentials";
            case "meta.lastPasswordReset" -> "lastPasswordReset";
            case "meta.mfaEnrollmentSkippedAt" -> "mfaEnrollmentSkippedAt";
            case "meta.accountLockedAt" -> "accountLockedAt";
            case "meta.accountLockedUntil" -> "accountLockedUntil";
            default -> filterName;
        };
    }

    private static String escapeRegexCharacters(String value) {
        // Iterate through the other regex characters and escape them.
        // A character like '|' becomes '\|' in the regex, which then becomes '\\|' in JSON.
        for (String ch : ALLOWED_REGEX_CHARACTERS) {
            value = value.replace(ch, "\\\\" + ch);
        }

        return value;
    }

    private static boolean isRegexOperator (String operator){
        return REGEX_OPERATORS.contains(operator);
    }

    private static String convertFilterValue(FilterCriteria criteria, String filterName, String operator) {
        if (valueSpecialCharsList.stream().anyMatch(s -> criteria.getFilterValue().contains(s))) {
            throw new IllegalArgumentException("Invalid filter value [" + criteria.getFilterValue() + FOUND_IN_THE_THE_SEARCH_QUERY);
        }

        String filterValue = criteria.getFilterValue();

        if (isDateInput(filterName)) {
            filterValue = "ISODate(\"" + filterValue + "\")";
            return filterValue;
        }
        if (isRegexOperator(operator)) {
            filterValue = escapeRegexCharacters(filterValue);
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
                "updatedAt".equals(filterName) ||
                "loggedAt".equals(filterName) ||
                "lastPasswordReset".equals(filterName) ||
                "lastLoginWithCredentials".equals(filterName) ||
                "mfaEnrollmentSkippedAt".equals(filterName) ||
                "accountLockedAt".equals(filterName) ||
                "accountLockedUntil".equals(filterName);
    }
}
