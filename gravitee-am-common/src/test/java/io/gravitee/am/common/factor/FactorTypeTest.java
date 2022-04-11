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
package io.gravitee.am.common.factor;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.NoSuchElementException;

import static io.gravitee.am.common.factor.FactorType.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(value = Parameterized.class)
public class FactorTypeTest {

    private final String factor;
    private final FactorType expected;
    private final boolean threwNseException;

    public FactorTypeTest(String factor, FactorType factorType, boolean threwException) {
        this.factor = factor;
        this.expected = factorType;
        this.threwNseException = threwException;
    }

    @Parameters
    public static Collection<Object []> params() {
        return Arrays.asList(new Object[][]{
                {"TOTP", OTP, false},
                {"HTTP", HTTP, false},
                {"SMS", SMS, false},
                {"EMAIL", EMAIL, false},
                {"CALL", CALL, false},
                {"nein", null, true},
                {"nej", null, true},
                {"no", null, true},
                {"non", null, true},
        });
    }

    @Test
    public void getFactorTypeFromString() {
        try {
            FactorType actual = FactorType.getFactorTypeFromString(factor);
            assertEquals(actual, expected);
        } catch (NoSuchElementException e) {
            assertTrue(threwNseException);
        }
    }
}