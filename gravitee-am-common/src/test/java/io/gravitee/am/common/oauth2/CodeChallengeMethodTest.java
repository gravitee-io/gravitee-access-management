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

    @ParameterizedTest
    // challenges generated using the following command:
    // $ python3 -c 'import base64,hashlib,sys; print(str(base64.urlsafe_b64encode(hashlib.sha256(sys.argv[1].encode("ascii")).digest()).replace(b"=",b""),"ascii"))' "<VERIFIER>"
    @CsvSource(textBlock = """
            5uper-secre7-v3rifier,lDzwTM99eiiW1qFHE5fhagGz2rKnSZ4iMxWKo29c6ZE
            lorem ipsum dolor sed amet,YOw3BJFqChrlp9vhd6EEK_IDA1UIbT2by1DRq_C8Qhc
            ' ',Nqnn8clbgv-5l0PgxcTOldg8mkMKrFn4TvPL-rYUUGg
            '',47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU
            ",ijMf3ecDLzOnHhsuJX2AFm40jgD8sXkU9IvbV6HGMAc
            """)
    void s256_challengeMeetsSpec(String verifier, String expectedChallenge) {
        assertThat(CodeChallengeMethod.S256.getChallenge(verifier)).isEqualTo(expectedChallenge);
    }

}
