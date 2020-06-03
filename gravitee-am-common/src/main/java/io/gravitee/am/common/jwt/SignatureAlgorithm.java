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
package io.gravitee.am.common.jwt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Type-safe representation of standard JWT signature algorithm names as defined in the
 * <a href="https://tools.ietf.org/html/draft-ietf-jose-json-web-algorithms-31">JSON Web Algorithms</a> specification.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum SignatureAlgorithm {

    /**
     * JWA name for {@code No digital signature or MAC performed}
     */
    NONE("none", "No digital signature or MAC performed", "None", null, null, false, 0, 0),

    /**
     * JWA algorithm name for {@code HMAC using SHA-256}
     */
    HS256("HS256", "HMAC using SHA-256", "HMAC", "HmacSHA256", "SHA-256", true, 256, 256),

    /**
     * JWA algorithm name for {@code HMAC using SHA-384}
     */
    HS384("HS384", "HMAC using SHA-384", "HMAC", "HmacSHA384", "SHA-384", true, 384, 384),

    /**
     * JWA algorithm name for {@code HMAC using SHA-512}
     */
    HS512("HS512", "HMAC using SHA-512", "HMAC", "HmacSHA512", "SHA-512", true, 512, 512),

    /**
     * JWA algorithm name for {@code RSASSA-PKCS-v1_5 using SHA-256}
     */
    RS256("RS256", "RSASSA-PKCS-v1_5 using SHA-256", "RSA", "SHA256withRSA", "SHA-256",  true, 256, 2048),

    /**
     * JWA algorithm name for {@code RSASSA-PKCS-v1_5 using SHA-384}
     */
    RS384("RS384", "RSASSA-PKCS-v1_5 using SHA-384", "RSA", "SHA384withRSA","SHA-384",  true, 384, 2048),

    /**
     * JWA algorithm name for {@code RSASSA-PKCS-v1_5 using SHA-512}
     */
    RS512("RS512", "RSASSA-PKCS-v1_5 using SHA-512", "RSA", "SHA512withRSA", "SHA-512", true, 512, 2048),

    /**
     * JWA algorithm name for {@code ECDSA using P-256 and SHA-256}
     */
    ES256("ES256", "ECDSA using P-256 and SHA-256", "ECDSA", "SHA256withECDSA", "SHA-256", true, 256, 256),

    /**
     * JWA algorithm name for {@code ECDSA using P-384 and SHA-384}
     */
    ES384("ES384", "ECDSA using P-384 and SHA-384", "ECDSA", "SHA384withECDSA", "SHA-384", true, 384, 384),

    /**
     * JWA algorithm name for {@code ECDSA using P-521 and SHA-512}
     */
    ES512("ES512", "ECDSA using P-521 and SHA-512", "ECDSA", "SHA512withECDSA", "SHA-512", true, 512, 521),

    /**
     * JWA algorithm name for {@code RSASSA-PSS using SHA-256 and MGF1 with SHA-256}.  <b>This algorithm requires
     * Java 11 or later or a JCA provider like BouncyCastle to be in the runtime classpath.</b>  If on Java 10 or
     * earlier, BouncyCastle will be used automatically if found in the runtime classpath.
     */
    PS256("PS256", "RSASSA-PSS using SHA-256 and MGF1 with SHA-256", "RSA", "RSASSA-PSS", "SHA-256",false, 256, 2048),

    /**
     * JWA algorithm name for {@code RSASSA-PSS using SHA-384 and MGF1 with SHA-384}.  <b>This algorithm requires
     * Java 11 or later or a JCA provider like BouncyCastle to be in the runtime classpath.</b>  If on Java 10 or
     * earlier, BouncyCastle will be used automatically if found in the runtime classpath.
     */
    PS384("PS384", "RSASSA-PSS using SHA-384 and MGF1 with SHA-384", "RSA", "RSASSA-PSS", "SHA-384",false, 384, 2048),

    /**
     * JWA algorithm name for {@code RSASSA-PSS using SHA-512 and MGF1 with SHA-512}. <b>This algorithm requires
     * Java 11 or later or a JCA provider like BouncyCastle to be in the runtime classpath.</b>  If on Java 10 or
     * earlier, BouncyCastle will be used automatically if found in the runtime classpath.
     */
    PS512("PS512", "RSASSA-PSS using SHA-512 and MGF1 with SHA-512", "RSA", "RSASSA-PSS", "SHA-512", false, 512, 2048);

    //purposefully ordered higher to lower:
    public static final List<SignatureAlgorithm> PREFERRED_HMAC_ALGS = Collections.unmodifiableList(Arrays.asList(
            SignatureAlgorithm.HS512, SignatureAlgorithm.HS384, SignatureAlgorithm.HS256));
    //purposefully ordered higher to lower:
    public static final List<SignatureAlgorithm> PREFERRED_EC_ALGS = Collections.unmodifiableList(Arrays.asList(
            SignatureAlgorithm.ES512, SignatureAlgorithm.ES384, SignatureAlgorithm.ES256));

    private final String value;
    private final String description;
    private final String familyName;
    private final String jcaName;
    private final String digestName;
    private final boolean jdkStandard;
    private final int digestLength;
    private final int minKeyLength;

    SignatureAlgorithm(String value,
                       String description,
                       String familyName,
                       String jcaName,
                       String digestName,
                       boolean jdkStandard,
                       int digestLength, int minKeyLength) {
        this.value = value;
        this.description = description;
        this.familyName = familyName;
        this.jcaName = jcaName;
        this.digestName = digestName;
        this.jdkStandard = jdkStandard;
        this.digestLength = digestLength;
        this.minKeyLength = minKeyLength;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public String getFamilyName() {
        return familyName;
    }

    public String getJcaName() {
        return jcaName;
    }

    public String getDigestName() {
        return digestName;
    }

    public boolean isJdkStandard() {
        return jdkStandard;
    }

    public boolean isHmac() {
        return familyName.equals("HMAC");
    }

    public boolean isRsa() {
        return familyName.equals("RSA");
    }

    public boolean isEllipticCurve() {
        return familyName.equals("ECDSA");
    }

    public int getDigestLength() {
        return digestLength;
    }

    public int getMinKeyLength() {
        return this.minKeyLength;
    }

}
