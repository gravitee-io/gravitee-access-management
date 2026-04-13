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
package io.gravitee.am.gateway.handler.aauth.signing;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReplayDetectorTest {

    @Test
    public void shouldAcceptFirstRequest() throws Exception {
        ReplayDetector detector = new ReplayDetector();
        detector.check("thumbprint-abc", 1712345678L);
        assertEquals(1, detector.size());
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldRejectDuplicateThumbprintCreatedPair() throws Exception {
        ReplayDetector detector = new ReplayDetector();
        detector.check("thumbprint-abc", 1712345678L);
        detector.check("thumbprint-abc", 1712345678L); // replay
    }

    @Test
    public void shouldAcceptDifferentCreatedTimestamp_sameKey() throws Exception {
        ReplayDetector detector = new ReplayDetector();
        detector.check("thumbprint-abc", 1712345678L);
        detector.check("thumbprint-abc", 1712345679L);
        assertEquals(2, detector.size());
    }

    @Test
    public void shouldAcceptSameCreated_differentKey() throws Exception {
        ReplayDetector detector = new ReplayDetector();
        detector.check("thumbprint-abc", 1712345678L);
        detector.check("thumbprint-xyz", 1712345678L);
        assertEquals(2, detector.size());
    }
}
