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
package io.gravitee.am.model.webprotection;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CspDirectiveTest {

    @Test
    public void shouldParseNameAndValue() {
        final CspDirective directive = CspDirective.parse("default-src 'self'");

        assertEquals("default-src", directive.name());
        assertEquals("'self'", directive.value());
    }

    @Test
    public void shouldParseWithOptionalTrailingSemicolon() {
        assertEquals(CspDirective.parse("default-src 'self'"), CspDirective.parse("default-src 'self';"));
    }

    @Test
    public void shouldKeepMultiTokenValueVerbatim() {
        final CspDirective directive = CspDirective.parse("script-src 'self' https://cdn.example.com 'unsafe-inline'");

        assertEquals("'self' https://cdn.example.com 'unsafe-inline'", directive.value());
    }

    @Test
    public void shouldParseValuelessDirective() {
        final CspDirective directive = CspDirective.parse("upgrade-insecure-requests");

        assertEquals("upgrade-insecure-requests", directive.name());
        assertEquals("", directive.value());
        assertFalse(directive.hasValue());
    }

    @Test
    public void shouldParseValuelessDirectiveWithTrailingSemicolon() {
        final CspDirective directive = CspDirective.parse("upgrade-insecure-requests;");

        assertEquals("upgrade-insecure-requests", directive.name());
        assertEquals("", directive.value());
    }

    @Test
    public void shouldCollapseExtraWhitespace() {
        final CspDirective directive = CspDirective.parse("  default-src\t  'self'  ");

        assertEquals("default-src", directive.name());
        assertEquals("'self'", directive.value());
    }

    @Test
    public void shouldReturnNullForBlankEntries() {
        assertNull(CspDirective.parse(null));
        assertNull(CspDirective.parse(""));
        assertNull(CspDirective.parse("   "));
        assertNull(CspDirective.parse(";"));
    }

    @Test
    public void shouldPreserveNameCasingButCanonicaliseForComparison() {
        final CspDirective directive = CspDirective.parse("Script-Src 'self'");

        assertEquals("Script-Src", directive.name());
        assertEquals("script-src", directive.canonicalName());
    }

    @Test
    public void shouldRenderBackToStorageForm() {
        assertEquals("default-src 'self'", CspDirective.parse("default-src 'self';").render());
        assertEquals("upgrade-insecure-requests", CspDirective.parse("upgrade-insecure-requests").render());
    }

    @Test
    public void shouldRoundTripThroughParseAndRender() {
        final String entry = "script-src 'self' https://cdn.example.com";

        assertEquals(entry, CspDirective.parse(CspDirective.parse(entry).render()).render());
    }

    @Test
    public void shouldStripOnlyOneTrailingSemicolon() {
        assertEquals("default-src 'self'", CspDirective.stripTrailingSemicolon("default-src 'self';"));
        assertEquals("default-src 'self'; script-src 'self'",
                CspDirective.stripTrailingSemicolon("default-src 'self'; script-src 'self';"));
    }

    @Test
    public void shouldIdentifyReportTargets() {
        assertTrue(CspDirective.parse("report-uri /csp-reports").isReportTarget());
        assertTrue(CspDirective.parse("Report-To csp-endpoint").isReportTarget());
        assertFalse(CspDirective.parse("default-src 'self'").isReportTarget());
    }

    @Test
    public void shouldValidateNameSyntax() {
        assertTrue(CspDirective.isValidName("default-src"));
        assertTrue(CspDirective.isValidName("require-trusted-types-for"));
        assertFalse(CspDirective.isValidName("script_src"));
        assertFalse(CspDirective.isValidName("Script-Src"));
        assertFalse(CspDirective.isValidName("default src"));
        assertFalse(CspDirective.isValidName(""));
        assertFalse(CspDirective.isValidName(null));
    }

    @Test
    public void shouldRejectCharactersNettyForbidsInAHeaderValue() {
        // Everything below 0x20 except tab, plus DEL.
        for (int code = 0; code < 0x20; code++) {
            if (code == '\t') {
                continue;
            }
            assertTrue("0x%02x should be rejected".formatted(code),
                    CspDirective.hasIllegalHeaderCharacter("'self'" + (char) code));
        }
        assertTrue(CspDirective.hasIllegalHeaderCharacter("'self'" + (char) 0x7f));
    }

    @Test
    public void shouldRejectCarriageReturnAndLineFeed() {
        assertTrue(CspDirective.hasIllegalHeaderCharacter("'self'" + (char) 0x0a + " more"));
        assertTrue(CspDirective.hasIllegalHeaderCharacter("'self'" + (char) 0x0d + (char) 0x0a + " more"));
    }

    @Test
    public void shouldAllowTabAndOrdinaryValueCharacters() {
        assertFalse(CspDirective.hasIllegalHeaderCharacter("'self'" + (char) 0x09 + "https://cdn.example.com"));
        assertFalse(CspDirective.hasIllegalHeaderCharacter("'self' https://cdn.example.com data:"));
        assertFalse(CspDirective.hasIllegalHeaderCharacter("https://caf\u00e9.example.com"));
        assertFalse(CspDirective.hasIllegalHeaderCharacter(""));
        assertFalse(CspDirective.hasIllegalHeaderCharacter(null));
    }

    @Test
    public void shouldNotRequireValueForValuelessDirectives() {
        assertFalse(CspDirective.requiresValue("upgrade-insecure-requests"));
        assertFalse(CspDirective.requiresValue("block-all-mixed-content"));
        assertFalse(CspDirective.requiresValue("sandbox"));
        assertFalse(CspDirective.requiresValue("trusted-types"));
        assertTrue(CspDirective.requiresValue("default-src"));
    }
}
