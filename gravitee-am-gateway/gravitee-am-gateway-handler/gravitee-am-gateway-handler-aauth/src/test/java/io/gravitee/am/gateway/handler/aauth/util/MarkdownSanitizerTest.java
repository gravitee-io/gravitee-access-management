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
package io.gravitee.am.gateway.handler.aauth.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link MarkdownSanitizer}.
 */
public class MarkdownSanitizerTest {

    @Test
    public void shouldReturnEmptyForNull() {
        assertEquals("", MarkdownSanitizer.toSafeHtml(null));
    }

    @Test
    public void shouldReturnEmptyForBlank() {
        assertEquals("", MarkdownSanitizer.toSafeHtml("   "));
    }

    @Test
    public void shouldRenderBoldMarkdown() {
        String result = MarkdownSanitizer.toSafeHtml("I need **read** access");
        assertTrue(result.contains("<strong>read</strong>"));
    }

    @Test
    public void shouldRenderListMarkdown() {
        String result = MarkdownSanitizer.toSafeHtml("- item one\n- item two");
        assertTrue(result.contains("<li>"));
        assertTrue(result.contains("item one"));
    }

    @Test
    public void shouldStripScriptTags() {
        String result = MarkdownSanitizer.toSafeHtml("Hello <script>alert('xss')</script> world");
        assertFalse(result.contains("<script>"));
        assertFalse(result.contains("alert"));
    }

    @Test
    public void shouldStripIframeTags() {
        String result = MarkdownSanitizer.toSafeHtml("<iframe src='https://evil.com'></iframe>");
        assertFalse(result.contains("<iframe"));
    }

    @Test
    public void shouldPreserveLinks() {
        String result = MarkdownSanitizer.toSafeHtml("[click here](https://example.com)");
        assertTrue(result.contains("<a"));
        assertTrue(result.contains("https://example.com"));
    }

    @Test
    public void shouldRenderCodeBlocks() {
        String result = MarkdownSanitizer.toSafeHtml("Use `data.read` scope");
        assertTrue(result.contains("<code>"));
        assertTrue(result.contains("data.read"));
    }
}
