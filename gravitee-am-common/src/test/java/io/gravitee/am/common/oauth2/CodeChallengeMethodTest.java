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
