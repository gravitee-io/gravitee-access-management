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
package io.gravitee.am.management.handlers.management.api.utils;

import io.gravitee.am.management.handlers.management.api.utils.RedirectUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author GraviteeSource Team
 */
public class RedirectUtilsTest {

    @Test
    public void testBuildRedirectUrl_redirectUriWithTrailingSlash() {
        // Test case: redirect_uri ends with slash, should be handled properly
        String result = RedirectUtils.buildCockpitRedirectUrl("https://example.com/", "/environments/env1");
        assertEquals("https://example.com/environments/env1", result);
    }

    @Test
    public void testBuildRedirectUrl_redirectUriWithoutTrailingSlash() {
        // Test case: redirect_uri doesn't end with slash, should work normally
        String result = RedirectUtils.buildCockpitRedirectUrl("https://example.com", "/environments/env1");
        assertEquals("https://example.com/environments/env1", result);
    }

    @Test
    public void testBuildRedirectUrl_complexUrlWithTrailingSlash() {
        // Test case: complex URL with path and trailing slash
        String result = RedirectUtils.buildCockpitRedirectUrl("https://subdomain.example.com:8080/path/", "/environments/env1");
        assertEquals("https://subdomain.example.com:8080/path/environments/env1", result);
    }

    @Test
    public void testBuildRedirectUrl_multipleTrailingSlashes() {
        // Test case: redirect_uri has multiple trailing slashes
        String result = RedirectUtils.buildCockpitRedirectUrl("https://example.com///", "/environments/env1");
        assertEquals("https://example.com/environments/env1", result);
    }

    @Test
    public void testBuildRedirectUrl_redirectPathWithoutLeadingSlash() {
        // Test case: redirectPath doesn't start with slash (should be added)
        String result = RedirectUtils.buildCockpitRedirectUrl("https://example.com", "environments/env1");
        assertEquals("https://example.com/environments/env1", result);
    }

    @Test
    public void testBuildRedirectUrl_redirectPathWithLeadingSlash() {
        // Test case: redirectPath already starts with slash
        String result = RedirectUtils.buildCockpitRedirectUrl("https://example.com", "/environments/env1");
        assertEquals("https://example.com/environments/env1", result);
    }

    @Test
    public void testBuildRedirectUrl_emptyRedirectPath() {
        // Test case: empty redirectPath
        String result = RedirectUtils.buildCockpitRedirectUrl("https://example.com/", "");
        assertEquals("https://example.com", result);
    }

    @Test
    public void testBuildRedirectUrl_nullRedirectUri() {
        // Test case: null redirectUri should return redirectPath
        String result = RedirectUtils.buildCockpitRedirectUrl(null, "/environments/env1");
        assertEquals("/environments/env1", result);
    }

    @Test
    public void testBuildRedirectUrl_nullRedirectUriAndEmptyPath() {
        // Test case: null redirectUri and empty path
        String result = RedirectUtils.buildCockpitRedirectUrl(null, "");
        assertEquals("", result);
    }

    @Test
    public void testBuildRedirectUrl_redirectPathWithoutLeadingSlashAndEmptyBase() {
        // Test case: redirectPath without leading slash and base URI without trailing slash
        String result = RedirectUtils.buildCockpitRedirectUrl("https://example.com", "environments/env1");
        assertEquals("https://example.com/environments/env1", result);
    }
}
