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
package io.gravitee.am.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.gravitee.am.common.oauth2.TokenType.ACCESS_TOKEN;
import static io.gravitee.am.common.oauth2.TokenType.ID_TOKEN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenExchangeSettingsTest {

    @Test
    void isValid_disabledIsAlwaysValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(false);
        settings.setAllowedSubjectTokenTypes(Collections.emptyList());
        settings.setAllowedRequestedTokenTypes(Collections.emptyList());
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_disabledWithNullListsIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(false);
        settings.setAllowedSubjectTokenTypes(null);
        settings.setAllowedRequestedTokenTypes(null);
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_enabledWithDefaultsIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_enabledWithEmptySubjectTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(Collections.emptyList());
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithNullSubjectTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(null);
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithEmptyRequestedTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedRequestedTokenTypes(Collections.emptyList());
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithNullRequestedTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedRequestedTokenTypes(null);
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithNeitherImpersonationNorDelegationIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(false);
        settings.setAllowDelegation(false);
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithDelegationAndEmptyActorTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(false);
        settings.setAllowDelegation(true);
        settings.setAllowedActorTokenTypes(Collections.emptyList());
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithDelegationAndNullActorTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(false);
        settings.setAllowDelegation(true);
        settings.setAllowedActorTokenTypes(null);
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithDelegationAndPopulatedActorTokenTypesIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(false);
        settings.setAllowDelegation(true);
        settings.setAllowedActorTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN)));
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_enabledWithImpersonationOnlyAndAllListsPopulatedIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(true);
        settings.setAllowDelegation(false);
        settings.setAllowedSubjectTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN)));
        settings.setAllowedRequestedTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN, ID_TOKEN)));
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_enabledWithImpersonationAndEmptyActorTokenTypesIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(true);
        settings.setAllowDelegation(false);
        settings.setAllowedActorTokenTypes(Collections.emptyList());
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_enabledWithBothModesAndAllListsPopulatedIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(true);
        settings.setAllowDelegation(true);
        settings.setAllowedSubjectTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN)));
        settings.setAllowedRequestedTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN, ID_TOKEN)));
        settings.setAllowedActorTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN)));
        assertTrue(settings.isValid());
    }
}
