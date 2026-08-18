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
package io.gravitee.am.service.validators;

import io.gravitee.am.model.webprotection.CspSettings;
import io.gravitee.am.service.exception.InvalidDomainException;
import io.gravitee.am.service.validators.webprotection.CspSettingsValidator;
import io.gravitee.am.service.validators.webprotection.CspSettingsValidatorImpl;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CspSettingsValidatorTest {

    private CspSettingsValidator validator;

    @Before
    public void before() {
        validator = new CspSettingsValidatorImpl();
    }

    private static CspSettings enabled(List<String> directives) {
        final CspSettings settings = new CspSettings();
        settings.setInherited(false);
        settings.setEnabled(true);
        settings.setDirectives(directives);
        return settings;
    }

    private void assertRejected(CspSettings settings, String expectedMessageFragment) {
        final AtomicReference<Throwable> captured = new AtomicReference<>();

        validator.validate(settings)
                .doOnError(captured::set)
                .test()
                .assertError(InvalidDomainException.class);

        final Throwable error = captured.get();
        assertNotNull("expected validation to fail", error);
        assertTrue("expected message to contain '" + expectedMessageFragment + "' but was: " + error.getMessage(),
                error.getMessage().contains(expectedMessageFragment));
    }

    // --- settings that carry no policy of their own -------------------------------------------------

    @Test
    public void shouldAcceptNullSettings() {
        validator.validate(null).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptInheritedSettingsWithoutDirectives() {
        final CspSettings settings = new CspSettings();
        settings.setInherited(true);

        validator.validate(settings).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptExplicitlyDisabledSettingsWithoutDirectives() {
        final CspSettings settings = new CspSettings();
        settings.setInherited(false);
        settings.setEnabled(false);

        validator.validate(settings).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptLegacySettingsThatInheritWhenDisabled() {
        // inherited == null is the legacy shape: enabled == false means inherit
        final CspSettings settings = new CspSettings();
        settings.setEnabled(false);

        validator.validate(settings).test().assertNoErrors();
    }

    // --- valid policies -----------------------------------------------------------------------------

    @Test
    public void shouldAcceptSimpleDirectives() {
        validator.validate(enabled(List.of("default-src 'self'", "script-src 'self' https://cdn.example.com")))
                .test()
                .assertNoErrors();
    }

    @Test
    public void shouldAcceptTrailingSemicolons() {
        validator.validate(enabled(List.of("default-src 'self';", "script-src 'self';")))
                .test()
                .assertNoErrors();
    }

    @Test
    public void shouldAcceptDirectivesThatTakeNoValue() {
        validator.validate(enabled(List.of("default-src 'self'", "upgrade-insecure-requests", "block-all-mixed-content")))
                .test()
                .assertNoErrors();
    }

    @Test
    public void shouldAcceptBareSandbox() {
        validator.validate(enabled(List.of("default-src 'self'", "sandbox"))).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptUnknownButSyntacticallyValidDirectiveName() {
        validator.validate(enabled(List.of("some-future-directive 'self'"))).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptMixedCaseDirectiveName() {
        validator.validate(enabled(List.of("Default-Src 'self'"))).test().assertNoErrors();
    }

    // --- rejections ---------------------------------------------------------------------------------

    @Test
    public void shouldRejectEmptyDirectiveList() {
        assertRejected(enabled(List.of()), "At least one CSP directive");
    }

    @Test
    public void shouldRejectNullDirectiveList() {
        assertRejected(enabled(null), "At least one CSP directive");
    }

    @Test
    public void shouldRejectBlankEntry() {
        assertRejected(enabled(Arrays.asList("default-src 'self'", "   ")), "must not be blank");
    }

    @Test
    public void shouldRejectInvalidDirectiveName() {
        assertRejected(enabled(List.of("script_src 'self'")), "'script_src' is not a valid CSP directive name");
    }

    @Test
    public void shouldRejectMissingValue() {
        assertRejected(enabled(List.of("default-src")), "'default-src' requires a value");
    }

    @Test
    public void shouldRejectDuplicateDirectiveNames() {
        assertRejected(enabled(List.of("default-src 'self'", "default-src 'none'")), "configured more than once");
    }

    @Test
    public void shouldRejectDuplicatesDifferingOnlyByCase() {
        assertRejected(enabled(List.of("script-src 'self'", "Script-Src 'none'")), "configured more than once");
    }

    @Test
    public void shouldRejectEmbeddedSemicolon() {
        assertRejected(enabled(List.of("default-src 'self'; script-src 'self'")), "must not contain ';'");
    }

    @Test
    public void shouldRejectEmbeddedSemicolonEvenWithTrailingSemicolon() {
        assertRejected(enabled(List.of("default-src 'self'; script-src 'self';")), "must not contain ';'");
    }

    @Test
    public void shouldRejectControlCharactersInAValue() {
        assertRejected(enabled(List.of("default-src 'self'" + (char) 0x01 + "https://cdn.example.com")),
                "must not contain control characters");
    }

    @Test
    public void shouldRejectLineFeedInAValue() {
        // Would otherwise fold into the header rather than fail, per Netty's obs-fold handling.
        assertRejected(enabled(List.of("default-src 'self'" + (char) 0x0a + " more")), "must not contain control characters");
    }

    @Test
    public void shouldRejectDeleteCharacterInAValue() {
        assertRejected(enabled(List.of("default-src 'self'" + (char) 0x7f)), "must not contain control characters");
    }

    @Test
    public void shouldAcceptTabInAValue() {
        validator.validate(enabled(List.of("default-src 'self'" + (char) 0x09 + "https://cdn.example.com")))
                .test()
                .assertNoErrors();
    }

    // --- report-only --------------------------------------------------------------------------------

    @Test
    public void shouldRejectReportOnlyWithoutReportTarget() {
        final CspSettings settings = enabled(List.of("default-src 'self'"));
        settings.setReportOnly(true);

        assertRejected(settings, "Report-only mode requires a 'report-uri' or 'report-to' directive");
    }

    @Test
    public void shouldRejectReportOnlyWhenReportTargetHasNoValue() {
        final CspSettings settings = enabled(List.of("default-src 'self'", "report-uri"));
        settings.setReportOnly(true);

        assertRejected(settings, "'report-uri' requires a value");
    }

    @Test
    public void shouldAcceptReportOnlyWithReportUri() {
        final CspSettings settings = enabled(List.of("default-src 'self'", "report-uri /csp-reports"));
        settings.setReportOnly(true);

        validator.validate(settings).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptReportOnlyWithReportTo() {
        final CspSettings settings = enabled(List.of("default-src 'self'", "report-to csp-endpoint"));
        settings.setReportOnly(true);

        validator.validate(settings).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptReportOnlyWithMixedCaseReportTarget() {
        final CspSettings settings = enabled(List.of("default-src 'self'", "Report-URI /csp-reports"));
        settings.setReportOnly(true);

        validator.validate(settings).test().assertNoErrors();
    }

    @Test
    public void shouldNotRequireReportTargetWhenNotReportOnly() {
        validator.validate(enabled(List.of("default-src 'self'"))).test().assertNoErrors();
    }
}
