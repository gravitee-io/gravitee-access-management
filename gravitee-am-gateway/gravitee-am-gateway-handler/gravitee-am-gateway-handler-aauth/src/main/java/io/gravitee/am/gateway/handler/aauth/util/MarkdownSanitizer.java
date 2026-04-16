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

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Renders CommonMark Markdown to sanitized HTML.
 * Per AAUTH spec Section 3: "Implementations MUST sanitize Markdown before rendering to users."
 *
 * @author GraviteeSource Team
 */
public final class MarkdownSanitizer {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

    private MarkdownSanitizer() {}

    /**
     * Parse a CommonMark Markdown string and return sanitized HTML.
     * Safe tags (p, strong, em, ul, ol, li, code, pre, a, blockquote, h1-h6)
     * are preserved; all others (script, iframe, etc.) are stripped.
     *
     * @param markdown the Markdown input, may be null
     * @return sanitized HTML, or empty string if input is null/blank
     */
    public static String toSafeHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String html = RENDERER.render(PARSER.parse(markdown));
        return Jsoup.clean(html, Safelist.relaxed());
    }
}
