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
package io.gravitee.am.model.account;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForgotPasswordLookupFieldTest {

    @Test
    public void shouldValidateKeys() {
        assertTrue(ForgotPasswordLookupField.isValidKey("employeeId"));
        assertTrue(ForgotPasswordLookupField.isValidKey("email"));
        assertFalse(ForgotPasswordLookupField.isValidKey("invalid key"));
    }

    @Test
    public void shouldMapFilterFieldNames() {
        assertEquals("emails.value", ForgotPasswordLookupField.toFilterFieldName("email"));
        assertEquals("userName", ForgotPasswordLookupField.toFilterFieldName("username"));
        assertEquals("additionalInformation.employeeId", ForgotPasswordLookupField.toFilterFieldName("employeeId"));
    }
}
