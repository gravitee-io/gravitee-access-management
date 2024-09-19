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
package io.gravitee.am.common.oauth2;

import io.gravitee.am.common.utils.SecureRandomString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


class CodeChallengeMethodTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
            # VALUE,EXPECTED
            plain,PLAIN
            pLaiN,PLAIN
            PLAIN,PLAIN
            s256,S256
            S256,S256,
            S 2 5 6,null
            _plain,null
            asdf,null
            """)
    void fromUriParam_shouldBeCaseInsensitive(String value, String expected) {
        if (expected.equals("null")) {
            assertThat(CodeChallengeMethod.fromUriParam(value)).isNull();
        } else {
            assertThat(CodeChallengeMethod.fromUriParam(value)).isEqualTo(CodeChallengeMethod.valueOf(expected));
        }
    }

    @Test
    void plain_challengeIsSameAsVerifier() {
        var verifier = SecureRandomString.generate();
        assertThat(CodeChallengeMethod.PLAIN.getChallenge(verifier)).isEqualTo(verifier);
    }

    @Test
    void s256_challengeMeetsSpec() {
        var verifier = "5uper-secre7-v3rifier";
        var expectedChallenge = "lDzwTM99eiiW1qFHE5fhagGz2rKnSZ4iMxWKo29c6ZE"; // = base64url_nopadding(sha256(verifier)))
        assertThat(CodeChallengeMethod.S256.getChallenge(verifier)).isEqualTo(expectedChallenge);
    }

}
