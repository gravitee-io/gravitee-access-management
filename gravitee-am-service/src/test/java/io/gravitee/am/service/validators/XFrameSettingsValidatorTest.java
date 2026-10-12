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

import io.gravitee.am.model.webprotection.XFrameSettings;
import io.gravitee.am.service.exception.InvalidDomainException;
import io.gravitee.am.service.validators.webprotection.XFrameSettingsValidator;
import io.gravitee.am.service.validators.webprotection.XFrameSettingsValidatorImpl;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class XFrameSettingsValidatorTest {

    private XFrameSettingsValidator validator;

    @Before
    public void before() {
        validator = new XFrameSettingsValidatorImpl();
    }

    private static XFrameSettings enabled(String action) {
        final XFrameSettings settings = new XFrameSettings();
        settings.setInherited(false);
        settings.setEnabled(true);
        settings.setAction(action);
        return settings;
    }

    private void assertRejected(XFrameSettings settings, String expectedMessageFragment) {
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

    @Test
    public void shouldAcceptNullSettings() {
        validator.validate(null).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptInheritedSettingsWithAnUnsupportedAction() {
        final XFrameSettings settings = new XFrameSettings();
        settings.setInherited(true);
        settings.setAction("ALLOW-FROM");

        validator.validate(settings).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptExplicitlyDisabledSettingsWithAnUnsupportedAction() {
        final XFrameSettings settings = new XFrameSettings();
        settings.setInherited(false);
        settings.setEnabled(false);
        settings.setAction("ALLOW-FROM");

        validator.validate(settings).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptLegacySettingsThatInheritWhenDisabled() {
        final XFrameSettings settings = new XFrameSettings();
        settings.setEnabled(false);
        settings.setAction("ALLOW-FROM");

        validator.validate(settings).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptDeny() {
        validator.validate(enabled("DENY")).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptSameOrigin() {
        validator.validate(enabled("SAMEORIGIN")).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptMixedCaseWithoutRewritingIt() {
        final XFrameSettings settings = enabled("deny");

        validator.validate(settings).test().assertNoErrors();

        assertEquals("deny", settings.getAction());
    }

    @Test
    public void shouldAcceptSurroundingWhitespaceWithoutRewritingIt() {
        final XFrameSettings settings = enabled(" SAMEORIGIN ");

        validator.validate(settings).test().assertNoErrors();

        assertEquals(" SAMEORIGIN ", settings.getAction());
    }

    @Test
    public void shouldAcceptNullActionWhenEnabled() {
        validator.validate(enabled(null)).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptBlankActionWhenEnabled() {
        validator.validate(enabled("   ")).test().assertNoErrors();
    }

    @Test
    public void shouldRejectAllowFrom() {
        assertRejected(enabled("ALLOW-FROM"), "'ALLOW-FROM'");
    }

    @Test
    public void shouldRejectAllowFromUrl() {
        assertRejected(enabled("ALLOW-FROM https://example.com"), "'ALLOW-FROM https://example.com'");
    }

    @Test
    public void shouldRejectAnyOtherToken() {
        assertRejected(enabled("BOGUS"), "'BOGUS'");
    }

    @Test
    public void shouldRejectLegacyEnabledSettingsWithAnUnsupportedAction() {
        final XFrameSettings settings = new XFrameSettings();
        settings.setEnabled(true);
        settings.setAction("BOGUS");

        assertRejected(settings, "'BOGUS'");
    }
}
