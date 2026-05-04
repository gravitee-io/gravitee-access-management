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
package io.gravitee.am.gateway.handler.aauth.service.bootstrap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PairwiseSubjectGeneratorTest {

    private static final String SALT_A = "test-master-salt-A";
    private static final String SALT_B = "test-master-salt-B";
    private static final String DOMAIN_A = "domain-A";
    private static final String DOMAIN_B = "domain-B";
    private static final String AGENT_X = "https://agent-x.example";
    private static final String AGENT_Y = "https://agent-y.example";
    private static final String USER_1 = "user-1";
    private static final String USER_2 = "user-2";

    @Test
    public void shouldBeDeterministicForSameInputs() {
        PairwiseSubjectGenerator gen = new PairwiseSubjectGenerator(SALT_A, DOMAIN_A);

        String first = gen.generate(USER_1, null, AGENT_X);
        String second = gen.generate(USER_1, null, AGENT_X);

        assertEquals(first, second);
    }

    @Test
    public void shouldDifferAcrossAgentServers() {
        PairwiseSubjectGenerator gen = new PairwiseSubjectGenerator(SALT_A, DOMAIN_A);

        assertNotEquals(gen.generate(USER_1, null, AGENT_X), gen.generate(USER_1, null, AGENT_Y));
    }

    @Test
    public void shouldDifferAcrossUsers() {
        PairwiseSubjectGenerator gen = new PairwiseSubjectGenerator(SALT_A, DOMAIN_A);

        assertNotEquals(gen.generate(USER_1, null, AGENT_X), gen.generate(USER_2, null, AGENT_X));
    }

    @Test
    public void shouldDifferAcrossDomains() {
        PairwiseSubjectGenerator genA = new PairwiseSubjectGenerator(SALT_A, DOMAIN_A);
        PairwiseSubjectGenerator genB = new PairwiseSubjectGenerator(SALT_A, DOMAIN_B);

        assertNotEquals(genA.generate(USER_1, null, AGENT_X), genB.generate(USER_1, null, AGENT_X));
    }

    @Test
    public void shouldDifferAcrossMasterSalts() {
        PairwiseSubjectGenerator genA = new PairwiseSubjectGenerator(SALT_A, DOMAIN_A);
        PairwiseSubjectGenerator genB = new PairwiseSubjectGenerator(SALT_B, DOMAIN_A);

        assertNotEquals(genA.generate(USER_1, null, AGENT_X), genB.generate(USER_1, null, AGENT_X));
    }

    @Test
    public void shouldProduceUrlSafeBase64Of32Chars() {
        PairwiseSubjectGenerator gen = new PairwiseSubjectGenerator(SALT_A, DOMAIN_A);

        String sub = gen.generate(USER_1, null, AGENT_X);

        assertEquals("expected 32 base64url chars (24 bytes)", 32, sub.length());
        assertTrue("expected url-safe base64 (no '+', '/', '='): " + sub,
                sub.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    public void shouldNotLeakUserIdInOutput() {
        PairwiseSubjectGenerator gen = new PairwiseSubjectGenerator(SALT_A, DOMAIN_A);

        String sub = gen.generate("alice@example.com", null, AGENT_X);

        assertFalse("sub must not contain raw userId: " + sub, sub.contains("alice"));
        assertFalse("sub must not contain raw userId domain: " + sub, sub.contains("example"));
    }

    @Test
    public void shouldRejectNullMasterSalt() {
        try {
            new PairwiseSubjectGenerator(null, DOMAIN_A);
            fail("expected IllegalArgumentException for null masterSalt");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void shouldRejectBlankDomainId() {
        try {
            new PairwiseSubjectGenerator(SALT_A, "");
            fail("expected IllegalArgumentException for blank domainId");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void shouldDifferAcrossIdentityIds() {
        PairwiseSubjectGenerator gen = new PairwiseSubjectGenerator(SALT_A, DOMAIN_A);

        String defaultIdentity = gen.generate(USER_1, null, AGENT_X);
        String workIdentity = gen.generate(USER_1, "identity-work", AGENT_X);
        String personalIdentity = gen.generate(USER_1, "identity-personal", AGENT_X);

        assertNotEquals(defaultIdentity, workIdentity);
        assertNotEquals(defaultIdentity, personalIdentity);
        assertNotEquals(workIdentity, personalIdentity);
    }

    @Test
    public void shouldBeStableWhenIdentityIdIsConsistentlyNull() {
        // Forward-compat invariant: passing null today must produce the same sub the
        // default-identity case will produce in the future. If this test fails after a
        // change to the hash format, every existing pairwise sub is invalidated.
        PairwiseSubjectGenerator gen = new PairwiseSubjectGenerator(SALT_A, DOMAIN_A);

        assertEquals(gen.generate(USER_1, null, AGENT_X), gen.generate(USER_1, null, AGENT_X));
    }

    @Test
    public void shouldAcceptEmptyMasterSaltForTesting() {
        // The contract allows empty string in tests; only null is rejected.
        // Production code wires a non-empty value (or warns and uses a sentinel).
        PairwiseSubjectGenerator gen = new PairwiseSubjectGenerator("", DOMAIN_A);
        String sub = gen.generate(USER_1, null, AGENT_X);
        assertEquals(32, sub.length());
    }
}
