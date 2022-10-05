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

package io.gravitee.am.gateway.handler.root.resources.endpoint;

import org.junit.Test;

import static org.junit.Assert.*;

public class ParamUtilsTest {

    @Test
    public void redirectMatch_url_with_path_success() {
        final String requestRedirectUri = "https://test.com/department/business";
        final String registeredRedirectUri = "https://test.com/department/*";

        boolean matched = ParamUtils.redirectMatches(requestRedirectUri, registeredRedirectUri, false);
        assertTrue( matched);
    }

    @Test
    public void redirectMatch_url_with_path_fail() {
        final String requestRedirectUri = "https://test.com/other/business";
        final String registeredRedirectUri = "https://test.com/department/*";

        boolean matched = ParamUtils.redirectMatches(requestRedirectUri, registeredRedirectUri, false);
        assertFalse( matched);
    }

    @Test
    public void redirectMatch_url_without_path_success() {
        final String requestRedirectUri = "https://test.com?id=10";
        final String registeredRedirectUri1 = "https://test.com/*";

        assertTrue(ParamUtils.redirectMatches(requestRedirectUri, registeredRedirectUri1, false));

        final String registeredRedirectUri2 = "https://test.com*";
        assertTrue(ParamUtils.redirectMatches(requestRedirectUri, registeredRedirectUri2, false));
    }

    @Test
    public void redirectMatch_url_path_with_param_success() {
        final String requestRedirectUri = "https://test.com/department?id=10";
        final String registeredRedirectUri = "https://test.com/department*";

        boolean matched = ParamUtils.redirectMatches(requestRedirectUri, registeredRedirectUri, false);
        assertTrue( matched);
    }

    @Test
    public void redirectMatch_url_without_path_fail() {
        final String requestRedirectUri = "https://test.com?id=10";
        final String registeredRedirectUri = "https://test.com/department*";

        boolean matched = ParamUtils.redirectMatches(requestRedirectUri, registeredRedirectUri, false);
        assertFalse( matched);
    }

    @Test
    public void redirectMatch_url_with_param_strict_fail() {
        final String requestRedirectUri = "https://test.com?id=10";
        final String registeredRedirectUri = "https://test.com";

        assertFalse(ParamUtils.redirectMatches(requestRedirectUri, registeredRedirectUri, true));

        final String registeredRedirectUriWildCard = "https://test.com*";
        assertFalse(ParamUtils.redirectMatches(requestRedirectUri, registeredRedirectUriWildCard, true));

        final String requestRedirectUriParam = "https://test.com/people";
        assertFalse(ParamUtils.redirectMatches(requestRedirectUriParam, registeredRedirectUri, true));

        final String requestRedirectUriParamQuery = "https://test.com/people?v=123";
        assertFalse(ParamUtils.redirectMatches(requestRedirectUriParamQuery, registeredRedirectUri, true));
    }
}
