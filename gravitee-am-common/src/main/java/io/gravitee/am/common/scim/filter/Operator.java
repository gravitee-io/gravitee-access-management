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
package io.gravitee.am.common.scim.filter;

/**
 *    +----------+-------------+------------------------------------------+
 *    | Operator | Description | Behavior                                 |
 *    +----------+-------------+------------------------------------------+
 *    | eq       | equal       | The attribute and operator values must   |
 *    |          |             | be identical for a match.                |
 *    |          |             |                                          |
 *    | ne       | not equal   | The attribute and operator values are    |
 *    |          |             | not identical.                           |
 *    |          |             |                                          |
 *    | co       | contains    | The entire operator value must be a      |
 *    |          |             | substring of the attribute value for a   |
 *    |          |             | match.                                   |
 *    |          |             |                                          |
 *    | sw       | starts with | The entire operator value must be a      |
 *    |          |             | substring of the attribute value,        |
 *    |          |             | starting at the beginning of the         |
 *    |          |             | attribute value.  This criterion is      |
 *    |          |             | satisfied if the two strings are         |
 *    |          |             | identical.                               |
 *    |          |             |                                          |
 *    | ew       | ends with   | The entire operator value must be a      |
 *    |          |             | substring of the attribute value,        |
 *    |          |             | matching at the end of the attribute     |
 *    |          |             | value.  This criterion is satisfied if   |
 *    |          |             | the two strings are identical.           |
 *    |          |             |                                          |
 *    | pr       | present     | If the attribute has a non-empty or      |
 *    |          | (has value) | non-null value, or if it contains a      |
 *    |          |             | non-empty node for complex attributes,   |
 *    |          |             | there is a match.                        |
 *    |          |             |                                          |
 *    | gt       | greater     | If the attribute value is greater than   |
 *    |          | than        | the operator value, there is a match.    |
 *    |          |             | The actual comparison is dependent on    |
 *    |          |             | the attribute type.  For string          |
 *    |          |             | attribute types, this is a               |
 *    |          |             | lexicographical comparison, and for      |
 *    |          |             | DateTime types, it is a chronological    |
 *    |          |             | comparison.  For integer attributes, it  |
 *    |          |             | is a comparison by numeric value.        |
 *    |          |             | Boolean and Binary attributes SHALL      |
 *    |          |             | cause a failed response (HTTP status     |
 *    |          |             | code 400) with "scimType" of             |
 *    |          |             | "invalidFilter".                         |
 *    |          |             |                                          |
 *    | ge       | greater     | If the attribute value is greater than   |
 *    |          | than or     | or equal to the operator value, there is |
 *    |          | equal to    | a match.  The actual comparison is       |
 *    |          |             | dependent on the attribute type.  For    |
 *    |          |             | string attribute types, this is a        |
 *    |          |             | lexicographical comparison, and for      |
 *    |          |             | DateTime types, it is a chronological    |
 *    |          |             | comparison.  For integer attributes, it  |
 *    |          |             | is a comparison by numeric value.        |
 *    |          |             | Boolean and Binary attributes SHALL      |
 *    |          |             | cause a failed response (HTTP status     |
 *    |          |             | code 400) with "scimType" of             |
 *    |          |             | "invalidFilter".                         |
 *    |          |             |                                          |
 *    | lt       | less than   | If the attribute value is less than the  |
 *    |          |             | operator value, there is a match.  The   |
 *    |          |             | actual comparison is dependent on the    |
 *    |          |             | attribute type.  For string attribute    |
 *    |          |             | types, this is a lexicographical         |
 *    |          |             | comparison, and for DateTime types, it   |
 *    |          |             | is a chronological comparison.  For      |
 *    |          |             | integer attributes, it is a comparison   |
 *    |          |             | by numeric value.  Boolean and Binary    |
 *    |          |             | attributes SHALL cause a failed response |
 *    |          |             | (HTTP status code 400) with "scimType"   |
 *    |          |             | of "invalidFilter".                      |
 *    |          |             |                                          |
 *    | le       | less than   | If the attribute value is less than or   |
 *    |          | or equal to | equal to the operator value, there is a  |
 *    |          |             | match.  The actual comparison is         |
 *    |          |             | dependent on the attribute type.  For    |
 *    |          |             | string attribute types, this is a        |
 *    |          |             | lexicographical comparison, and for      |
 *    |          |             | DateTime types, it is a chronological    |
 *    |          |             | comparison.  For integer attributes, it  |
 *    |          |             | is a comparison by numeric value.        |
 *    |          |             | Boolean and Binary attributes SHALL      |
 *    |          |             | cause a failed response (HTTP status     |
 *    |          |             | code 400) with "scimType" of             |
 *    |          |             | "invalidFilter".                         |
 *    +----------+-------------+------------------------------------------+
 *
 *                        Table 3: Attribute Operators
 *
 *    +----------+-------------+------------------------------------------+
 *    | Operator | Description | Behavior                                 |
 *    +----------+-------------+------------------------------------------+
 *    | and      | Logical     | The filter is only a match if both       |
 *    |          | "and"       | expressions evaluate to true.            |
 *    |          |             |                                          |
 *    | or       | Logical     | The filter is a match if either          |
 *    |          | "or"        | expression evaluates to true.            |
 *    |          |             |                                          |
 *    | not      | "Not"       | The filter is a match if the expression  |
 *    |          | function    | evaluates to false.                      |
 *    +----------+-------------+------------------------------------------+
 *
 *                         Table 4: Logical Operators
 *
 *
 *    +----------+-------------+------------------------------------------+
 *    | Operator | Description | Behavior                                 |
 *    +----------+-------------+------------------------------------------+
 *    | ( )      | Precedence  | Boolean expressions MAY be grouped using |
 *    |          | grouping    | parentheses to change the standard order |
 *    |          |             | of operations, i.e., to evaluate logical |
 *    |          |             | "or" operators before logical "and"      |
 *    |          |             | operators.                               |
 *    |          |             |                                          |
 *    | [ ]      | Complex     | Service providers MAY support complex    |
 *    |          | attribute   | filters where expressions MUST be        |
 *    |          | filter      | applied to the same value of a parent    |
 *    |          | grouping    | attribute specified immediately before   |
 *    |          |             | the left square bracket ("[").  The      |
 *    |          |             | expression within square brackets ("["   |
 *    |          |             | and "]") MUST be a valid filter          |
 *    |          |             | expression based upon sub-attributes of  |
 *    |          |             | the parent attribute.  Nested            |
 *    |          |             | expressions MAY be used.  See examples   |
 *    |          |             | below.                                   |
 *    +----------+-------------+------------------------------------------+
 *
 *                         Table 5: Grouping Operators
 *
 * See <a href="https://tools.ietf.org/html/rfc7644#section-3.4.2.2">3.4.2.2. Filtering</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum Operator {

    AND("and"),
    OR("or"),
    EQUALITY("eq"),
    NOT_EQUAL("ne"),
    CONTAINS("co"),
    STARTS_WITH("sw"),
    ENDS_WITH("ew"),
    PRESENCE("pr"),
    GREATER_THAN("gt"),
    GREATER_OR_EQUAL("ge"),
    LESS_THAN("lt"),
    LESS_OR_EQUAL("le");

    private final String value;

    Operator(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Operator fromString(String value) {
        for (Operator o: Operator.values()) {
            if (o.value.equalsIgnoreCase(value)) {
                return o;
            }
        }
        throw new IllegalArgumentException("No operator with value [" + value + "] found");
    }
}
