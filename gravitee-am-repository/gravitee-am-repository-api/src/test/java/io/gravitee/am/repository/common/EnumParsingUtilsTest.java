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
package io.gravitee.am.repository.common;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class EnumParsingUtilsTest {

    @Mock
    private Logger logger;

    private enum SampleEnum { A, B }

    @Test
    public void safeValueOfReturnsNullAndNotUnknownWhenRawIsNull() {
        SampleEnum parsed = EnumParsingUtils.safeValueOf(SampleEnum.class, null, "id1", "field", logger);

        assertNull(parsed);
        assertFalse(EnumParsingUtils.isUnknown(null, parsed));
    }

    @Test
    public void safeValueOfParsesKnownValueAndIsNotUnknown() {
        SampleEnum parsed = EnumParsingUtils.safeValueOf(SampleEnum.class, "A", "id1", "field", logger);

        assertEquals(SampleEnum.A, parsed);
        assertFalse(EnumParsingUtils.isUnknown("A", parsed));
    }

    @Test
    public void safeValueOfReturnsNullAndLogsWarnOncePerUnknownValue() {
        SampleEnum parsed = EnumParsingUtils.safeValueOf(SampleEnum.class, "FUTURE", "id1", "field", logger);

        assertNull(parsed);
        assertTrue(EnumParsingUtils.isUnknown("FUTURE", parsed));
        verify(logger, times(1)).warn(anyString(), eq("SampleEnum"), eq("FUTURE"), eq("id1"), eq("field"));

        // throttled on same unknown value
        EnumParsingUtils.safeValueOf(SampleEnum.class, "FUTURE", "id2", "field", logger);
        verify(logger, times(1)).warn(anyString(), any(), any(), any(), any());
    }

    @Test
    public void logDiscardWarnsThenDebugsOncePerEntityId() {
        String id = "id-warn-then-debug";
        String reason = "reason";

        EnumParsingUtils.logDiscard(id, logger, reason);
        EnumParsingUtils.logDiscard(id, logger, reason);

        verify(logger).warn(contains("Discarding entity"), eq(id), eq(reason));
        verify(logger).debug(contains("Discarding entity"), eq(id), eq(reason));
    }
}
