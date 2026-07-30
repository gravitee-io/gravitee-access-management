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
package io.gravitee.am.gateway.handler.root.service.user.model;

import io.gravitee.am.repository.management.api.search.FilterCriteria;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForgotPasswordParametersTest {

    @Test
    public void shouldBuildCriteriaForEmail() {
        FilterCriteria criteria = new ForgotPasswordParameters("user@test.com", false, false).buildCriteria();
        assertEquals("emails.value", criteria.getFilterName());
        assertEquals("user@test.com", criteria.getFilterValue());
    }

    @Test
    public void shouldBuildCriteriaForCustomAttribute() {
        FilterCriteria criteria = new ForgotPasswordParameters(
                Map.of("employeeId", "E-42"),
                true,
                false).buildCriteria();

        assertEquals("additionalInformation.employeeId", criteria.getFilterName());
        assertEquals("E-42", criteria.getFilterValue());
    }

    @Test
    public void shouldBuildAndCriteriaForMultipleFields() {
        FilterCriteria criteria = new ForgotPasswordParameters(
                Map.of("email", "user@test.com", "employeeId", "E-42"),
                true,
                false).buildCriteria();

        assertEquals("and", criteria.getOperator());
        assertEquals(2, criteria.getFilterComponents().size());
    }

    @Test
    public void shouldDetectLookupValues() {
        assertTrue(new ForgotPasswordParameters(Map.of("employeeId", "E-42"), true, false).hasAnyLookupValue());
        assertFalse(new ForgotPasswordParameters(Map.of(), true, false).hasAnyLookupValue());
        assertTrue(new ForgotPasswordParameters("user@test.com", false, false).canFallbackToIdentityProvider());
        assertFalse(new ForgotPasswordParameters(Map.of("employeeId", "E-42"), true, false).canFallbackToIdentityProvider());
    }
}
