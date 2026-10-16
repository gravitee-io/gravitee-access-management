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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void shouldAcceptStandardAttributeNames() {
        assertNotNull(SCIMFilterParser.parse("userName eq \"alice\""));
        assertNotNull(SCIMFilterParser.parse("emails.value co \"@example.com\""));
        assertNotNull(SCIMFilterParser.parse("meta.created gt \"2024-01-01T00:00:00.000Z\""));
        assertNotNull(SCIMFilterParser.parse("name.givenName sw \"Al\""));
        assertNotNull(SCIMFilterParser.parse("additionalInformation.custom_key eq \"v\""));
        assertNotNull(SCIMFilterParser.parse("active eq true"));
    }

    @Test
    public void shouldAcceptUrnPrefixedAttributeName() {
        Filter filter = SCIMFilterParser.parse("urn:ietf:params:scim:schemas:core:2.0:User:userName eq \"alice\"");
        assertNotNull(filter);
        assertTrue("urn:ietf:params:scim:schemas:core:2.0:User:userName".equals(filter.getFilterAttribute().toString()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectAttributeNameWithSqlTautology() {
        SCIMFilterParser.parse("1=1/**/or/**/1=1 pr");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectAttributeNameWithQuote() {
        SCIMFilterParser.parse("us'ername eq \"x\"");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectJsonKeyWithQuote() {
        SCIMFilterParser.parse("additionalInformation.ab'c eq \"x\"");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectUnknownSchemaPrefix() {
        SCIMFilterParser.parse("1=1/**/or/**/1=1:userName pr");
    }

    @Test
    public void shouldReportInvalidFilterAttributeMessage() {
        try {
            SCIMFilterParser.parse("1=1/**/or/**/1=1 pr");
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid filter attribute"));
        }
    }
}
