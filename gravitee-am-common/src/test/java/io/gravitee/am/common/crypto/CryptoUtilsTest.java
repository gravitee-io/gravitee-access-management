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
package io.gravitee.am.common.crypto;

import io.gravitee.am.common.utils.SecureRandomString;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class CryptoUtilsTest {

    @Test
    void roundTrip() {
        var defaultSecret = "s3cR3t4grAv1t3310AMS1g1ingDftK3y";
        var sourceKey = new SecretKeySpec(defaultSecret.getBytes(), "HmacSHA512");


        var text = SecureRandomString.generate();
        var encrypted = CryptoUtils.encrypt(text, sourceKey);
        assertThat(CryptoUtils.decrypt(encrypted, sourceKey)).isEqualTo(text);
    }

    @Test
    void missingInput_shouldThrowException() {
        var defaultSecret = "s3cR3t4grAv1t3310AMS1g1ingDftK3y";
        var sourceKey = new SecretKeySpec(defaultSecret.getBytes(), "HmacSHA512");

        assertThatThrownBy(()->CryptoUtils.decrypt("aaaaaa$bbbbbb", sourceKey)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->CryptoUtils.decrypt("aaaaaa", sourceKey)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedInput_shouldThrowExceptionWithMinimumInfo() {
        try (MockedStatic<Cipher> crypto = Mockito.mockStatic(Cipher.class)) {
            crypto.when(() -> Cipher.getInstance(any())).thenThrow(new RuntimeException("Some error"));
            var defaultSecret = "s3cR3t4grAv1t3310AMS1g1ingDftK3y";
            var sourceKey = new SecretKeySpec(defaultSecret.getBytes(), "HmacSHA512");

            var encoder = Base64.getUrlEncoder();
            var input = encoder.encodeToString("aaaa".getBytes()) + "$" + encoder.encodeToString("bbbb".getBytes()) + "$" + encoder.encodeToString("cccc".getBytes());

            assertThatThrownBy(() -> CryptoUtils.decrypt(input, sourceKey))
                    .isExactlyInstanceOf(RuntimeException.class)
                    .hasMessageMatching("\\[errorId=.+] Error decrypting data");
        }
    }

}
