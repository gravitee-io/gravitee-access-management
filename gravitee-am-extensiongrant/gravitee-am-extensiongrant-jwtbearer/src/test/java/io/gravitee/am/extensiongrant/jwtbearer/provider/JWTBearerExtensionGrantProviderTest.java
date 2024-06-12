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
package io.gravitee.am.extensiongrant.jwtbearer.provider;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.jwtbearer.JWTBearerExtensionGrantConfiguration;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.repository.oauth2.model.request.TokenRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.extensiongrant.jwtbearer.provider.JWTBearerExtensionGrantProvider.SSH_PUB_KEY;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class JWTBearerExtensionGrantProviderTest {

    private static final String ECDSA_SHA2_NISTP_256 = "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBMNI1NY25sDkulGU2qZ+ntPvDMR4l8CkUg3ishgYMH1VC18hudiF3yzYZZEFqsNFni9RbQv9Du5TGv8a3ZQ9Bdc=";
    private static final String EC256_JWT_TOKEN = "eyJraWQiOiJjMWYxMjZiMi05YjQzLTQ3ZDktYWRjMy00OTRiMzAwOWM1M2IiLCJhbGciOiJFUzI1NiJ9.ew0KICAic3ViIjogImpvaG5kb2UiLA0KICAibmFtZSI6ICJKb2huIERvZSIsDQogICJpYXQiOiAxNTE2MjM5MDIyDQp9.c4xpf_uKKrYLguXxDgzVzZjt4k_1EjCW0gBE4enT3D_oU9PmLaOqGDkNbbzN_H6yFI2g122_b5m_YDNXmXbvEw";
    private static final String EC384_JWT_TOKEN = "eyJraWQiOiJlOGU1NTQ4My03ZmRjLTQ2NTYtODE4Ni1lODc0ZjBlOTlmYTMiLCJhbGciOiJFUzM4NCJ9.ew0KICAic3ViIjogIjEyMzQ1Njc4OTAiLA0KICAibmFtZSI6ICJBbmlzaCBOYXRoIiwNCiAgImlhdCI6IDE1MTYyMzkwMjINCn0.OMhoToOoSXO_FTiF14tBdEgKmbZbN7b9bI-sdLtUVrL8VlwpH0vG80JM_wfPXWrtWf-NvMdVNdqlnPUoxBX29Bl9MBVyZ7KgcczaDnB94nPpIVq3FClsqve32JbvvJOp";
    private static final String ECDSA_SHA2_NISTP_384 = "ecdsa-sha2-nistp384 AAAAE2VjZHNhLXNoYTItbmlzdHAzODQAAAAIbmlzdHAzODQAAABhBARyFCAPaRRgC9beKnOnIlDjiuAGRxudKJ7sv19j1N6BSs68hBPbqf/Ma+FjiBDvJDd9WhjdtcDsR4S8JFJOUbIJAwLkV6HGSI0KwfPl2UdQS5u1wVAjYLGR2OSZ+5fnBA==";
    private static final String ECDSA_SHA_2_NISTP_521 = "ecdsa-sha2-nistp521 AAAAE2VjZHNhLXNoYTItbmlzdHA1MjEAAAAIbmlzdHA1MjEAAACFBAHne1/vD6+b6DgUBALyi5y1vo+eABcmwXfqi1+tuMXGcbStgyFm37XrPRGJv0sSRvbulBxsyjAw3DpM+9htJTo2NgAjtW7s4HCweem3KWi+qXkhtHbzL3VcNIuiiXQziUc2KWmIEgjw0VAVu1Wpoe1wng02QueVmcwyIfevmxxYNQCZ1A==";
    private static final String EC512_JWT_TOKEN = "eyJraWQiOiI3YzFhZTFiMC0zYzc0LTRhYmQtOTcyNi0xODFlOWRmZDYyZGQiLCJhbGciOiJFUzUxMiJ9.ew0KICAic3ViIjogImpvaG5kb2UiLA0KICAibmFtZSI6ICJKb2huIERvZSIsDQogICJpYXQiOiAxNTE2MjM5MDIyDQp9.AKCYQT95BsaUwekWxj6ZXhqrEArOQo7yAQYajc-cHRwGoK6RNdnCYFEFEclwoyiO6ykntSFdAyoE3Xb6-wQTrGwjACuksP0JEm3Mgb2Qz0MhHRvg77AXr9rneFwfIyntrvwR-yQe5cLXwzUgEWuaaOvzwHgVLFP3etxVIz-uYEC-Oc7d";

    @InjectMocks
    private JWTBearerExtensionGrantProvider jwtBearerExtensionGrantProvider = new JWTBearerExtensionGrantProvider();

    @Mock
    private JWTBearerExtensionGrantConfiguration jwtBearerTokenGranterConfiguration;

    @Test
    public void testParseKey_RSA() {
        final String key = "ssh-rsa AAAAE2VjZHNhLXNoYTItbmlzdHAyNTY=";
        final String key2 = "ssh-rsa AAAAE2VjZHNhLXNoYTItbmlzdHAyNTY= test@test.com";
        final String key3 = "ssh";
        final String key4 = "ssh-rsa";
        assertTrue(SSH_PUB_KEY.matcher(key).matches());
        assertTrue(SSH_PUB_KEY.matcher(key2).matches());
        assertFalse(SSH_PUB_KEY.matcher(key3).matches());
        assertFalse(SSH_PUB_KEY.matcher(key4).matches());
    }

    @Test
    public void testParseKey_ECDSA() {
        final String key = "ecdsa AAAAE2VjZHNhLXNoYTItbmlzdHAyNTY=";
        final String key2 = "ecdsa-sha2-xyz AAAAE2VjZHNhLXNoYTItbmlzdHAyNTY=";
        final String key3 = "ecdsa-sha2-xyz AAAAE2VjZHNhLXNoYTItbmlzdHAyNTY= test@test.com";
        final String key4 = "ecdsa";
        final String key5 = "ecdsa-sha2";
        assertTrue(SSH_PUB_KEY.matcher(key).matches());
        assertTrue(SSH_PUB_KEY.matcher(key2).matches());
        assertTrue(SSH_PUB_KEY.matcher(key3).matches());
        assertFalse(SSH_PUB_KEY.matcher(key4).matches());
        assertFalse(SSH_PUB_KEY.matcher(key5).matches());
    }

    @Test
    public void testCreateUser_withClaimsMapper() {
        List<Map<String, String>> claimsMapper = new ArrayList<>();
        Map<String, String> claimMapper1 = new HashMap<>();
        claimMapper1.put("assertion_claim", "username");
        claimMapper1.put("token_claim", "username");

        Map<String, String> claimMapper2 = new HashMap<>();
        claimMapper2.put("assertion_claim", "email");
        claimMapper2.put("token_claim", "email");

        claimsMapper.add(claimMapper1);
        claimsMapper.add(claimMapper2);

        when(jwtBearerTokenGranterConfiguration.getClaimsMapper()).thenReturn(claimsMapper);

        Map<String, Object> assertionClaims = new HashMap<>();
        assertionClaims.put("username", "test_username");
        assertionClaims.put("email", "test_email");

        User user = jwtBearerExtensionGrantProvider.createUser(new JWT(assertionClaims));

        assertEquals(3, user.getAdditionalInformation().values().size());
        assertEquals("test_username", user.getAdditionalInformation().get("username"));
        assertEquals("test_email", user.getAdditionalInformation().get("email"));
    }

    @Test
    public void must_grant_with_rsa256() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn("ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCewTYTy2Rp0n5Zx+XsjeFHHsgwyv3NgKZal0oeN4o7bUAlzg9c+gxWhGp67LIH3ty/JmysJlgNpp5EKq/RGLIuqOekT9wt03LREUJhUoVbWHFXCfZUJyS58mkO706y0NddRlhmolAIl5T4f2blH5c1iaO/C6WXXXhk7WQWZZfQS0Tji99K/OabSUkfcc90Upkjgr6whYzsuQGDwefJfo6ozxF25pY0OVF1tgTZOeOqXivFv3qqgKvuUMhMENLbcTN5AITYpx55kmPM6Qa+O7z2o7V9ApR+J1qLW4OqvyXHTLexmPy6CqD7QdV2MvDjTMhD6LiB54z3tDXcCiqmYp2d");

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", "eyJraWQiOiI5MWE3OTk0MS1kYjQ2LTQ1ODMtYjhlYy03NjU2NjNhNDg1YzIiLCJhbGciOiJSUzI1NiJ9.ew0KICAic3ViIjogImpvaG5kb2UiLA0KICAibmFtZSI6ICJqb2huZG9lIiwNCiAgImlhdCI6IDE1MTYyMzkwMjINCn0.UAEtcSFK4mYcmWnBTpXpIMN6Y9XVAxISJVkBKDDAuJ2E5-81bL_bsfBXjNd9CBIKi9UVQHehMF52BVa4yzDzefWnxlKzbqjMV4QK4UjZj_mXahcHUBRYsPAtJUIDsboqZca9fbnwolTXjO_Kl9ObYz_veqmYuZRvR8wNOQnnDO8IM1QtWN_-_S0Zi0ifh2aaBoJQsB2WvlmpXZZslQbLPu-FwXrZy7GpZJ7Sx_j59OF5Dyu0R26PHFwJx-rxwfRgkIDDlgH--DWpFG58EQzhpYRJKp4xAb0W1Uc9fYdDzrnzF-zcf2B038UCHKhotPQHBNYaa1LJA5jTAG9nN6dJYg"));

        jwtBearerExtensionGrantProvider.afterPropertiesSet();
        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * read EC key
     * Private-Key: (256 bit)
     * pub:
     *     04:c3:48:d4:d6:36:e6:c0:e4:ba:51:94:da:a6:7e:
     *     9e:d3:ef:0c:c4:78:97:c0:a4:52:0d:e2:b2:18:18:
     *     30:7d:55:0b:5f:21:b9:d8:85:df:2c:d8:65:91:05:
     *     aa:c3:45:9e:2f:51:6d:0b:fd:0e:ee:53:1a:ff:1a:
     *     dd:94:3d:05:d7
     * ASN1 OID: prime256v1
     * NIST CURVE: P-256
     **/
    @Test
    public void must_grant_with_ecdsa_sha2_nistp256() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_256);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC256_JWT_TOKEN));

        jwtBearerExtensionGrantProvider.afterPropertiesSet();
        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * read EC key
     * Private-Key: (384 bit)
     * pub:
     *     04:04:72:14:20:0f:69:14:60:0b:d6:de:2a:73:a7:
     *     22:50:e3:8a:e0:06:47:1b:9d:28:9e:ec:bf:5f:63:
     *     d4:de:81:4a:ce:bc:84:13:db:a9:ff:cc:6b:e1:63:
     *     88:10:ef:24:37:7d:5a:18:dd:b5:c0:ec:47:84:bc:
     *     24:52:4e:51:b2:09:03:02:e4:57:a1:c6:48:8d:0a:
     *     c1:f3:e5:d9:47:50:4b:9b:b5:c1:50:23:60:b1:91:
     *     d8:e4:99:fb:97:e7:04
     * ASN1 OID: secp384r1
     * NIST CURVE: P-384
     */
    @Test
    public void must_grant_with_ecdsa_sha2_nistp384() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_384);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC384_JWT_TOKEN));

        jwtBearerExtensionGrantProvider.afterPropertiesSet();
        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * read EC key
     * Private-Key: (521 bit)
     * pub:
     *     04:01:e7:7b:5f:ef:0f:af:9b:e8:38:14:04:02:f2:
     *     8b:9c:b5:be:8f:9e:00:17:26:c1:77:ea:8b:5f:ad:
     *     b8:c5:c6:71:b4:ad:83:21:66:df:b5:eb:3d:11:89:
     *     bf:4b:12:46:f6:ee:94:1c:6c:ca:30:30:dc:3a:4c:
     *     fb:d8:6d:25:3a:36:36:00:23:b5:6e:ec:e0:70:b0:
     *     79:e9:b7:29:68:be:a9:79:21:b4:76:f3:2f:75:5c:
     *     34:8b:a2:89:74:33:89:47:36:29:69:88:12:08:f0:
     *     d1:50:15:bb:55:a9:a1:ed:70:9e:0d:36:42:e7:95:
     *     99:cc:32:21:f7:af:9b:1c:58:35:00:99:d4
     * ASN1 OID: secp521r1
     * NIST CURVE: P-521
     **/
    @Test
    public void must_grant_with_ecdsa_sha2_nistp512() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA_2_NISTP_521);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC512_JWT_TOKEN));

        jwtBearerExtensionGrantProvider.afterPropertiesSet();
        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    @Test
    public void must_fail_due_to_wrong_publickey_with_wrong_assertion() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA_2_NISTP_521);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC256_JWT_TOKEN));

        jwtBearerExtensionGrantProvider.afterPropertiesSet();
        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidGrantException.class);
    }

    @Test
    public void must_fail_due_to_wrong_publickey_prefix() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn("wrong prefix");

        assertThrows(InvalidKeyException.class, () -> jwtBearerExtensionGrantProvider.afterPropertiesSet());
    }

    @Test
    public void must_fail_due_to_null_assertion() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA_2_NISTP_521);

        final TokenRequest tokenRequest = new TokenRequest();

        jwtBearerExtensionGrantProvider.afterPropertiesSet();
        assertThrows(InvalidGrantException.class, () -> jwtBearerExtensionGrantProvider.grant(tokenRequest).blockingGet());
    }
}
