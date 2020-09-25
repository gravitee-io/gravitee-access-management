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
package io.gravitee.am.common.scim.parser;

import io.gravitee.am.common.scim.filter.Filter;
import io.gravitee.am.common.scim.filter.Operator;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SCIMFilterParserTest {

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotParse_invalidFilter() {
        SCIMFilterParser.parse("invalid-value");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotParse_invalidFilter2() {
        SCIMFilterParser.parse("userName eq");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotParse_invalidFilter3() {
        SCIMFilterParser.parse("userName eq \"test\" or");
    }

    @Test
    public void shouldParse_compose_filter() {
        Filter filter = SCIMFilterParser.parse("userType eq 1.99 and (emails co \"example.com\" or emails.value co \"example.org\")");
        assertNotNull(filter);
        assertNotNull(filter.getFilterComponents());
        // global operator should be "and"
        assertTrue(filter.getOperator().equals(Operator.AND));
        // left operator should be "eq"
        assertTrue(filter.getFilterComponents().get(0).getOperator().equals(Operator.EQUALITY));
        // right operator should be "or"
        assertTrue(filter.getFilterComponents().get(1).getOperator().equals(Operator.OR));
    }

    @Test
    public void shouldParse_present_filter() {
        Filter filter = SCIMFilterParser.parse("title pr");
        assertNotNull(filter);
        assertNull(filter.getFilterComponents());
        assertTrue(filter.getOperator().equals(Operator.PRESENCE));
        assertTrue("title".equals(filter.getFilterAttribute().toString()));
        assertNull(filter.getFilterValue());
    }
}
