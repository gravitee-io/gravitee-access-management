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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.impl;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.proc.BadJWSException;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.model.TrustedIssuer;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Unit tests for {@link TrustedIssuerResolverImpl}.
 * Tests PEM-based verification with pre-generated RSA keys and certificates.
 */
public class TrustedIssuerResolverImplTest {

    // Pre-generated RSA certificate and private key pair (trusted)
    private static final String TRUSTED_CERT_PEM =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICwTCCAamgAwIBAgIIW0knWhPYcsowDQYJKoZIhvcNAQEMBQAwDzENMAsGA1UE\n" +
            "AxMEVGVzdDAeFw0yNjAyMjAxMjI5NDJaFw0yNzAyMjAxMjI5NDJaMA8xDTALBgNV\n" +
            "BAMTBFRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDiOtRBiFmS\n" +
            "RKD3lx13RHgFFJaAKmgieZnJh+BL2BgKHccncrhy+7py05KSBK068MBe7tOF0Ctn\n" +
            "7oE76fl5lZiz7R5cDhvhtTW36phR3RhY5BMrk06j8AvzthYwI+X2s/AxiEuqebPg\n" +
            "zmfjlQ4TAuIQc/pCcrNU7UHe67szmWJ8R0L2zWDVr4tx1Pw5RgsglUELjmRadFfA\n" +
            "rU8DVi8mjRVXH+s3SMQ9H8gppkm2a7IMlJRM/bVOh1rwryGeJc4kJDgk2pzmYC1J\n" +
            "eJ5+2aD83ObEYs9ZItCwp3/W7ETS/uqBKK4Gq1ns4UHTB6Q3nGTHU5vcWiSZjYCm\n" +
            "Lzsu7zBdiIOVAgMBAAGjITAfMB0GA1UdDgQWBBR7NcS0/yZk9APBRY7Qesang8s1\n" +
            "gjANBgkqhkiG9w0BAQwFAAOCAQEAt6s0D0Y6sJkSbm12IwadUlc8Eb9fssWgBjzJ\n" +
            "RnQRku6Ub7YtjV2eS9uYScb5P9nIgHvSxoV6TOjHNG3JIv3ZesgcP/kRLuet+kn3\n" +
            "vZhpWlj9zZ64NaldWv+ZpD+wZKX1MvRpsQuPO4nkYLWjP3AYRyY/Hwjv2Z0izRj2\n" +
            "4L6DD3v3hU1W5l/t7xsza88LOo0OHjrRg0kQJy9cYujQf4N1NMP/Qm8ZETc4s1Nq\n" +
            "8BWOPnykzbYmac4Mh1eMAbhu/2I7/nYG0ovPoyYru6V/dgbegPFfOKXgcybyGJKe\n" +
            "RR1k0b+BEQwNJ6dQ780SblKvPFXBsp0qnS3MBjbm+DwWajx1DQ==\n" +
            "-----END CERTIFICATE-----";

    private static final String TRUSTED_PRIVATE_KEY_PEM =
            "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDiOtRBiFmSRKD3\n" +
            "lx13RHgFFJaAKmgieZnJh+BL2BgKHccncrhy+7py05KSBK068MBe7tOF0Ctn7oE7\n" +
            "6fl5lZiz7R5cDhvhtTW36phR3RhY5BMrk06j8AvzthYwI+X2s/AxiEuqebPgzmfj\n" +
            "lQ4TAuIQc/pCcrNU7UHe67szmWJ8R0L2zWDVr4tx1Pw5RgsglUELjmRadFfArU8D\n" +
            "Vi8mjRVXH+s3SMQ9H8gppkm2a7IMlJRM/bVOh1rwryGeJc4kJDgk2pzmYC1JeJ5+\n" +
            "2aD83ObEYs9ZItCwp3/W7ETS/uqBKK4Gq1ns4UHTB6Q3nGTHU5vcWiSZjYCmLzsu\n" +
            "7zBdiIOVAgMBAAECggEAK0avL1kOr2cO9sXy1kuj/O7PCnXyRTQHSfDC5KdSE+9V\n" +
            "11M/+wjyBgC4j1OzMqz208IyduzXPNK4aJZtyYcnNrYTom71gPQJ8mR/XluhynFY\n" +
            "xHNxrfUfyC9rJ6raVRrfRg5pURNmaEj7wSKUlmjtB1I0S61G5mZbfTIRdcGwAA9C\n" +
            "qbSPgpxgqX3DxgZtiVgnypYaiKsuMYXOpxtgwJm0k7GUkHHb/zGObH2/H26LPxhv\n" +
            "Di8VRg9rV4Tss8pJxOoMqtKYvKTYniZIZ85QEdN0iBUA7qe2mFadT3VmujQl1aOJ\n" +
            "uhKVC1OFqxNFb+jfLgneZf7TD6Xkq7iBwQ9SG7WnmQKBgQDuX/GldT1v2WTZJdvW\n" +
            "7T6tWEmb0Dq5S1J+E9OmdvZcVBT8nbf+RgCFRhcq+CZDfRkAQRM5v8W58cXdSDmk\n" +
            "8anMRbFVnNXV6OB1CqlkMs4LVeopp6/3Tkgv65sBX+GHMx1nhzVZ7KIL1wtFQR4C\n" +
            "QAyyi1DBUc76oqLrIQ0RwnJU/QKBgQDy9QAA7HPRR5jNljszdGr1WpOjQKxPftB1\n" +
            "BFE1D7yuBytfxE0L7Exm/ttpb5MKF2ZBvIKU5VkwGFLRfUbuewTLrLK4C+f4wvGu\n" +
            "ky0mx2HsYmrsuyE8VAH4MD2e6zO6AI7YDeOU1CIiCYFMsDuEk1BGdbtd+Lw28gd8\n" +
            "iFxW/rM4eQKBgQCcQpa0oNsqQ/cBBflLte/dUD/IfULRjpfAAB1BRUCQG6o6QuWH\n" +
            "MLpMozqyt0LWAN5vtTj2JUlncB8FxL/M6YImXxU1iv7+H49sChYqkcdR1PsQXVVT\n" +
            "p6RYjXjp+MFtkEtZP1/w19cOLeS8fEhAr13jeMHySD+HOy/TNLJNjwrFvQKBgQDU\n" +
            "Yz7kj8/TVH6MwlbwbUYPRGYp4aCAQgDjOqnu89niXFwbdNRRpvlHVGXkbugge0Im\n" +
            "FzAkD7Z+59SGU7jNQ3d2wXrej3HzMh/ql7hx3PyKk6KbXh74yQuLtkg4A66NXG2D\n" +
            "C+k2MpfAH4UL0EfHZqxXXBY2WPnYIo5O3Dm0xyVPKQKBgQCzgFwEgNPVo2kgheOj\n" +
            "ntAmjfgRH4RM29cvgnhreDSVz2eCHJGiIqggKAp8WZcMYUj9sgPavGJ5nddjfXf7\n" +
            "PJJiF4btQKsEwHBX0Ieyrz6G4wQ5NWLFV9MgY5J5stLxh1fuzw4/Wgus9XCf8COH\n" +
            "5xun7YBRO5n7rtJULdtiOHWbIA==";

    // Pre-generated RSA private key (untrusted — different key pair, not matching the cert above)
    private static final String UNTRUSTED_PRIVATE_KEY_PEM =
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCuDjMOF13uUKWE\n" +
            "t0sfz7pecedFvv8Z26C1avZqj5W1mBv+ryfYlWgwsU2e+pjplmzNx5Urd1lt+GFV\n" +
            "4gwnk1eOO3b8pU/U7dvMsFPQ6iWgT21PVoHO9wL8cAWslcW71P5zl+SBdJ8r6j9x\n" +
            "IXr+4d2kmGFPBXK/MY3mpaCClp29BtSR27sPMFAT/CtyLE4QRFUY94N4geIl989m\n" +
            "pHl4DtLuy5CdYIjv+6c0vwZXqCKpDHBTjg5eeE4abQTzULW3r35MS4UoRBs0hRfj\n" +
            "JVuTt+t+wmx5Ko8YtcCukjsX+kWDNuItBa+LERWAxdiyZX8/kuuRerOdx6Tb/5mf\n" +
            "H5mSurPpAgMBAAECggEAMZRB3sWxL+0w+LjtYUZepAB2DFv3dnolMQgi4P+9eVi2\n" +
            "wLlL9Fm6sAQRRDPF0uPSYltzlkoM41JZB4m4RJ2n1xABOL4uG00Vyxx/A4du6Fc3\n" +
            "n93YZAfghd+y/hI7nOFzCaI+qNF7dZroL2WGD4xvAi+VqFi48tU+wnBzZD9a9zi+\n" +
            "lRKoq2NTq1756bPARcSgSAf/qHatMbKO3XJXzhvq/OCUK1++6ktPUYt9XiD6eRBt\n" +
            "Nk2T+iUkhZ+EAj0vsDgfP1JIbLy28YZotxHlFvslHW0aA7hBsaMUuP207wBp1JUU\n" +
            "DyQJlT4oeSdNHiWinOt4t+GpAjFeJMByPDCrroUtsQKBgQDPNqZKyZA8ttLFo6CC\n" +
            "4XQSNY/dYTexm4gcBGoZHzG0tb+9KQ2YwKcXOeFRYWO2vfLGgqWd5HJRJGzM3jAk\n" +
            "oVxBIEO52VpOEWU7mRwwaYvBS+qXlKSfar1dW2X4+T7xxYMh4Q2DdW8XlVF1uma4\n" +
            "3t44zJId4ZUqt9yTZgTXtf/bGwKBgQDXCQWHHiGKzBfvzug/x61YtfuJj5sTTIKs\n" +
            "FpGhc6LLRhtx3wPA7aP3wCJz8E+adfLn31nYNTja+AeoH6SEZZc1nCEjzMkrA+T4\n" +
            "5v1thm6zUubeexBr11+y+IWkhFs2fZf8k9NpHlulgXs2TGBnOiuY/z1gM05IeE0O\n" +
            "Rxtq0j25SwKBgQCMdXbzav4iqarl3ayIC1sqnLQQVD30XoE8vKFOth5zaF+4UYOt\n" +
            "76lTzSA2kdWNoeRXO2gYRypWZmJ53IK10cs/Oieuau8TzCba50Z8ao8ZT+SNK20L\n" +
            "wsbp6XKN+iX4rPHenTcTzR6o1caKDvhiiHAKAGFrb+Y1NryGDblqnyv30QKBgQCD\n" +
            "/WqiNFF8Y6gxr3wJYiQ59oIuPrJ+VxFCVhwP0O3U/fRsoeoo6vUhZpL/PTtvYQS3\n" +
            "ZPY96vU6GtKAVOPjzIPTCUGiOtokCCDs0sQuDT033yQM3dcHisyYC0nk4MUoHlFD\n" +
            "XO2AcXzpix+5BYqK8j6+i7T9rqBXhVgu8mCW4fO3HwKBgGG16M0D5AKYocQ6ooGT\n" +
            "y1bha+rZVLDU/V1l2FccAkq+TUS7naoakfU5bC1ZnuY2y+ocK9+eAeJbSEKw1fcF\n" +
            "ruCmaj85hmAJ1hjOFIGfIcCqci3TSE3ZmWTNU3bQjDtfFy8VuDp0TVUQylBvEPHM\n" +
            "r2ayOMqoLpAf7GOYAbk4/T/m";

    private TrustedIssuerResolverImpl resolver;

    @Before
    public void setUp() {
        resolver = new TrustedIssuerResolverImpl();
    }

    @Test
    public void shouldVerifyJwtWithValidPemCertificate() throws Exception {
        TrustedIssuer issuer = pemIssuer("https://trusted.example.com", TRUSTED_CERT_PEM);

        String jwt = signJwt(TRUSTED_PRIVATE_KEY_PEM, "https://trusted.example.com", "user-123");

        JWTClaimsSet claims = resolver.resolve(jwt, issuer);

        assertNotNull(claims);
        assertEquals("user-123", claims.getSubject());
        assertEquals("https://trusted.example.com", claims.getIssuer());
    }

    @Test(expected = BadJWSException.class)
    public void shouldRejectJwtSignedWithWrongKey() throws Exception {
        TrustedIssuer issuer = pemIssuer("https://trusted.example.com", TRUSTED_CERT_PEM);

        // Sign with untrusted key but claim trusted issuer
        String jwt = signJwt(UNTRUSTED_PRIVATE_KEY_PEM, "https://trusted.example.com", "user-123");

        resolver.resolve(jwt, issuer);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowOnInvalidPemCertificate() throws Exception {
        TrustedIssuer issuer = pemIssuer("https://bad.example.com", "not-a-valid-pem");

        String jwt = signJwt(TRUSTED_PRIVATE_KEY_PEM, "https://bad.example.com", "user-123");

        resolver.resolve(jwt, issuer);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowOnUnsupportedKeyResolutionMethod() throws Exception {
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://unsupported.example.com");
        issuer.setKeyResolutionMethod("UNKNOWN_METHOD");

        String jwt = signJwt(TRUSTED_PRIVATE_KEY_PEM, "https://unsupported.example.com", "user-123");

        resolver.resolve(jwt, issuer);
    }

    @Test
    public void shouldCacheProcessorForSameIssuer() throws Exception {
        TrustedIssuer issuer = pemIssuer("https://cached.example.com", TRUSTED_CERT_PEM);

        String jwt1 = signJwt(TRUSTED_PRIVATE_KEY_PEM, "https://cached.example.com", "user-1");
        String jwt2 = signJwt(TRUSTED_PRIVATE_KEY_PEM, "https://cached.example.com", "user-2");

        JWTClaimsSet claims1 = resolver.resolve(jwt1, issuer);
        JWTClaimsSet claims2 = resolver.resolve(jwt2, issuer);

        assertEquals("user-1", claims1.getSubject());
        assertEquals("user-2", claims2.getSubject());
    }

    // --- Helpers ---

    private static TrustedIssuer pemIssuer(String issuerUrl, String certificate) {
        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer(issuerUrl);
        ti.setKeyResolutionMethod(TrustedIssuer.KEY_RESOLUTION_PEM);
        ti.setCertificate(certificate);
        return ti;
    }

    private static PrivateKey loadPrivateKey(String base64Key) throws Exception {
        byte[] keyBytes = Base64.getMimeDecoder().decode(base64Key);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private static String signJwt(String privateKeyPem, String issuer, String subject) throws Exception {
        PrivateKey privateKey = loadPrivateKey(privateKeyPem);
        JWSSigner signer = new RSASSASigner(privateKey);
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .build();
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }
}
