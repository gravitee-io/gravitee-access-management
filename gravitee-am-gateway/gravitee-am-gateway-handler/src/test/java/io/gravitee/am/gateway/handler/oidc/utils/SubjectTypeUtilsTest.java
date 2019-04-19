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
package io.gravitee.am.gateway.handler.oidc.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(JUnit4.class)
public class SubjectTypeUtilsTest {

    @Test
    public void isSupportedSubjectType_nok() {
        assertFalse("should not be supported", SubjectTypeUtils.isValidSubjectType("unknown"));
    }

    @Test
    public void isSupportedSubjectType_ok() {
        assertTrue("should be supported", SubjectTypeUtils.isValidSubjectType("public"));
    }

    @Test
    public void supportedSubjectTypeList() {
        assertTrue("should have at least public", SubjectTypeUtils.getSupportedSubjectTypes().contains("public"));
    }
}
