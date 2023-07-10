/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.common.scim.parser;

import io.gravitee.am.common.scim.filter.AttributePath;
import io.gravitee.am.common.scim.filter.Filter;
import io.gravitee.am.common.scim.filter.Operator;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SCIMFilterParserTest {

    @ParameterizedTest(name = "Should not parse with [{0}] throws [{1}.]")
    @MethodSource("params_that_should_not_parse")
    public void shouldNotParse_invalidFilter(String filterString, Class<? extends Throwable> expected) {
        assertThrows(expected, () -> SCIMFilterParser.parse(filterString));
    }

    public static Stream<Arguments> params_that_should_not_parse() {
        return Stream.of(
                Arguments.of("", IllegalArgumentException.class),
                Arguments.of("invalid-", IllegalArgumentException.class),
                Arguments.of("(", IllegalArgumentException.class),
                Arguments.of(")", IllegalArgumentException.class),
                Arguments.of("( userName eq \"test\"", IllegalArgumentException.class),
                Arguments.of(" userName eq \"test\" )", IllegalArgumentException.class),
                Arguments.of("userName eq", IllegalArgumentException.class),
                Arguments.of("userName eq \"test\" or ", IllegalArgumentException.class),
                Arguments.of("userName eq \"test\" or ( userName eq \"test\" and )", IllegalArgumentException.class)
        );
    }

    @ParameterizedTest(name = "Should parse [{0}]")
    @MethodSource("params_that_should_parse")
    public void should_parse_several_filters(String filter, Filter expected) {
        final Filter actual = SCIMFilterParser.parse(filter);

        assertNotNull(actual);
        assertEquals(actual.getOperator(), expected.getOperator());

        final List<Filter> actualFilters = actual.getFilterComponents();
        final List<Filter> expectedFilters = expected.getFilterComponents();

        assertNotNull(actualFilters);
        assertEquals(expectedFilters.size(), actualFilters.size());

        testFilters(actualFilters, expectedFilters);

        var actualSubFilters = getCollection(expectedFilters, Filter::getFilterComponents).stream().flatMap(List::stream).collect(toList());
        var expectedSubFilters = getCollection(expectedFilters, Filter::getFilterComponents).stream().flatMap(List::stream).collect(toList());

        testFilters(actualSubFilters, expectedSubFilters);
    }

    private static void testFilters(List<Filter> actualFilters, List<Filter> expectedFilters) {
        assertIterableEquals(
                getCollection(expectedFilters, Filter::getFilterValue), getCollection(actualFilters, Filter::getFilterValue)
        );

        assertIterableEquals(
                getCollection(expectedFilters, Filter::getOperator), getCollection(actualFilters, Filter::getOperator)
        );

        assertIterableEquals(
                getCollection(expectedFilters, SCIMFilterParserTest::getAttributeName), getCollection(actualFilters, SCIMFilterParserTest::getAttributeName)
        );
    }

    private static <T> List<T> getCollection(List<Filter> expectedFilters, Function<Filter, T> mapper) {
        return expectedFilters.stream().map(mapper).filter(Objects::nonNull).collect(toList());
    }

    private static String getAttributeName(Filter f) {
        final AttributePath filterAttribute = f.getFilterAttribute();
        return filterAttribute == null ? null : filterAttribute.getAttributeName();
    }

    public static Stream<Arguments> params_that_should_parse() {
        return Stream.of(
                Arguments.of(
                        "userType eq 1.99 and (emails co \"example.com\" or emails.value co \"example.org\")",
                        createFilterComponents(Operator.AND,
                                createFilter("userType", Operator.EQUALITY, "1.99"),
                                createFilterComponents(Operator.OR,
                                        createFilter("emails", Operator.CONTAINS, "example.com"),
                                        createFilter("emails.value", Operator.CONTAINS, "example.org")
                                )
                        )
                ),
                Arguments.of(
                        "username ew \"doe\" or (organization eq \"acme\" and verified eq \"true\")",
                        createFilterComponents(Operator.OR,
                                createFilter("username", Operator.ENDS_WITH, "doe"),
                                createFilterComponents(Operator.AND,
                                        createFilter("organization", Operator.EQUALITY, "example.com"),
                                        createFilter("verified", Operator.EQUALITY, "TRUE")
                                )
                        )
                )
        );
    }

    private static Filter createFilterComponents(Operator operator, Filter... filterComponents) {
        return createFilter(null, operator, null, Arrays.asList(filterComponents));
    }

    private static Filter createFilter(String attributeName, Operator operator, String attributeValue) {
        return createFilter(attributeName, operator, attributeValue, null);
    }

    private static Filter createFilter(String attributeName, Operator operator, String attributeValue, List<Filter> filterComponents) {
        return new Filter(operator, getAttribute(attributeName), attributeValue, true, filterComponents);
    }

    private static AttributePath getAttribute(String attributeName) {
        return attributeName == null ? null : new AttributePath(
                "urn:ietf:params:scim:schemas:core:2.0",
                attributeName,
                null);
    }

    @Test
    public void shouldParse_present_filter() {
        Filter filter = SCIMFilterParser.parse("title pr");
        assertNotNull(filter);
        assertNull(filter.getFilterComponents());
        assertEquals(filter.getOperator(), Operator.PRESENCE);
        assertEquals("title", filter.getFilterAttribute().toString());
        assertNull(filter.getFilterValue());
    }
}
