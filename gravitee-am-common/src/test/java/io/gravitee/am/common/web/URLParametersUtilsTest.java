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
package io.gravitee.am.common.web;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author GraviteeSource Team
 */
public class URLParametersUtilsTest {

    @Test
    public void parse_shouldReadNameValuePairs() {
        Map<String, String> result = URLParametersUtils.parse("code=abc&state=xyz");

        assertEquals("abc", result.get("code"));
        assertEquals("xyz", result.get("state"));
    }

    @Test
    public void parse_shouldKeepAmpersandInValue_whenFragmentHasNoSeparator() {
        // An unencoded '&' inside a value must stay part of that value, not split into a new pair.
        Map<String, String> result = URLParametersUtils.parse("access_token=abc&raw&state=xyz");

        assertEquals("abc&raw", result.get("access_token"));
        assertEquals("xyz", result.get("state"));
        assertFalse(result.containsKey("raw"));
    }

    @Test
    public void parse_shouldKeepMultipleAmpersandsInValue() {
        // Several unencoded '&' in a row inside a value are all preserved.
        Map<String, String> result = URLParametersUtils.parse("access_token=abc&raw&more&state=xyz");

        assertEquals("abc&raw&more", result.get("access_token"));
        assertEquals("xyz", result.get("state"));
        assertEquals(2, result.size());
    }

    @Test
    public void parse_shouldKeepMultipleAmpersandsInValueEvenInFinalValue() {
        // Several unencoded '&' in a row inside a value are all preserved.
        Map<String, String> result = URLParametersUtils.parse("access_token=abc&raw&more&state=xy&zz");

        assertEquals("abc&raw&more", result.get("access_token"));
        assertEquals("xy&zz", result.get("state"));
        assertEquals(2, result.size());
    }
}
