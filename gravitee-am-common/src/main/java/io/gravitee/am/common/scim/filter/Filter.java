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

import java.util.List;

/**
 * SCIM filters MUST conform to the following ABNF [RFC5234] rules as
 *    specified below:
 *
 *      FILTER    = attrExp / logExp / valuePath / *1"not" "(" FILTER ")"
 *
 *      valuePath = attrPath "[" valFilter "]"
 *                  ; FILTER uses sub-attributes of a parent attrPath
 *
 *      valFilter = attrExp / logExp / *1"not" "(" valFilter ")"
 *
 *      attrExp   = (attrPath SP "pr") /
 *                  (attrPath SP compareOp SP compValue)
 *
 *      logExp    = FILTER SP ("and" / "or") SP FILTER
 *
 *      compValue = false / null / true / number / string
 *                  ; rules from JSON (RFC 7159)
 *
 *      compareOp = "eq" / "ne" / "co" /
 *                         "sw" / "ew" /
 *                         "gt" / "lt" /
 *                         "ge" / "le"
 *
 *      attrPath  = [URI ":"] ATTRNAME *1subAttr
 *                  ; SCIM attribute name
 *                  ; URI is SCIM "schema" URI
 *
 *      ATTRNAME  = ALPHA *(nameChar)
 *
 *      nameChar  = "-" / "_" / DIGIT / ALPHA
 *
 *      subAttr   = "." ATTRNAME
 *                  ; a sub-attribute of a complex attribute
 *
 * See <a href="https://tools.ietf.org/html/rfc7644#section-3.4.2.2">3.4.2.2. Filtering</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Filter {

    /**
     * The filter type.
     */
    private final Operator operator;

    /**
     * The attribute or sub-attribute to filter by, or {@code null} if not
     * applicable.
     */
    private final AttributePath filterAttribute;

    /**
     * The filter attribute value, or {@code null} if not applicable.
     */
    private final String filterValue;

    /**
     * Specifies whether the filter value is quoted in the string representation
     * of the filter. String and DateTime values are quoted. Integer and Boolean
     * values are not quoted.
     */
    private final boolean quoteFilterValue;

    /**
     * The filter components for 'or' and 'and' filter types, or {@code null}
     * if not applicable.
     */
    private final List<Filter> filterComponents;

    /**
     * Create a new SCIM filter from the provided information.
     *
     * @param operator        The filter type.
     * @param filterAttribute   The attribute or sub-attribute to filter by, or
     *                          {@code null} if not applicable.
     * @param filterValue       The filter attribute value, or {@code null} if not
     *                          applicable.
     * @param quoteFilterValue  Specifies whether the filter value is quoted in
     *                          the string representation of the filter.
     * @param filterComponents  The filter components for 'or' and 'and' filter
     *                          types, or {@code null} if not applicable.
     */
    public Filter(final Operator operator,
                  final AttributePath filterAttribute,
                  final String filterValue,
                  final boolean quoteFilterValue,
                  final List<Filter> filterComponents) {
        this.operator = operator;
        this.filterAttribute   = filterAttribute;
        this.filterValue       = filterValue;
        this.quoteFilterValue  = quoteFilterValue;
        this.filterComponents  = filterComponents;
    }

    public Operator getOperator() {
        return operator;
    }

    public AttributePath getFilterAttribute() {
        return filterAttribute;
    }

    public String getFilterValue() {
        return filterValue;
    }

    public boolean isQuoteFilterValue() {
        return quoteFilterValue;
    }

    public List<Filter> getFilterComponents() {
        return filterComponents;
    }
}
