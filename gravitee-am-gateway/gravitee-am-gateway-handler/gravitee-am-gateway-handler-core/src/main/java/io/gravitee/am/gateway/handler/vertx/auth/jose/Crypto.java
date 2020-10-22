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
package io.gravitee.am.gateway.handler.vertx.auth.jose;

import javax.crypto.Mac;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.UUID;

/**
 * Internal common interface for all crypto algorithms.
 * This is just an utility in order to simplfy sign and verify operations.
 *
 * @author Paulo Lopes
 */
// TODO to remove when updating to vert.x 4
public interface Crypto {

    String[] ECDSA_ALGORITHMS = {
            "SHA256withECDSA",
            "SHA384withECDSA",
            "SHA512withECDSA"
    };

    /**
     * The key id or null.
     */
    default String getId() {
        return null;
    }

    /**
     * A not null label for the key, labels are the same for same algorithm, kid objects
     * but not necessarily different internal keys/certificates
     */
    String getLabel();

    byte[] sign(byte[] payload);

    boolean verify(byte[] signature, byte[] payload);

    default boolean isECDSA(String algorithm) {
        for (String alg : ECDSA_ALGORITHMS) {
            if (alg.equals(algorithm)) {
                return true;
            }
        }

        return false;
    }

    default int ECDSALength(String algorithm) {
        switch (algorithm) {
            case "SHA256withECDSA":
                return 64;
            case "SHA384withECDSA":
                return 96;
            case "SHA512withECDSA":
                return 132;
        }

        return -1;
    }
}

/**
 * MAC based Crypto implementation
 *
 * @author Paulo Lopes
 */
class CryptoMac implements Crypto {

    private final String label = UUID.randomUUID().toString();
    private final Mac mac;

    CryptoMac(final Mac mac) {
        this.mac = mac;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public synchronized byte[] sign(byte[] payload) {
        return mac.doFinal(payload);
    }

    @Override
    public boolean verify(byte[] signature, byte[] payload) {
        return Arrays.equals(signature, sign(payload));
    }
}

/**
 * Public Key based Crypto implementation
 *
 * @author Paulo Lopes
 */
class CryptoKeyPair implements Crypto {

    private final String label = UUID.randomUUID().toString();

    private final Signature sig;
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final boolean ecdsa;
    private final int ecdsaSignatureLength;

    CryptoKeyPair(final String algorithm, final PublicKey publicKey, final PrivateKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.ecdsa = isECDSA(algorithm);
        this.ecdsaSignatureLength = ECDSALength(algorithm);

        Signature signature;
        try {
            // use default
            signature = Signature.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            // error
            throw new RuntimeException(e);
        }

        this.sig = signature;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public synchronized byte[] sign(byte[] payload) {
        if (privateKey == null) {
            throw new RuntimeException("Cannot sign (no private key)");
        }

        try {
            sig.initSign(privateKey);
            sig.update(payload);
            if (ecdsa) {
                return SignatureHelper.toJWS(sig.sign(), ecdsaSignatureLength);
            } else {
                return sig.sign();
            }
        } catch (SignatureException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized boolean verify(byte[] signature, byte[] payload) {
        if (publicKey == null) {
            throw new RuntimeException("Cannot verify (no public key)");
        }

        try {
            sig.initVerify(publicKey);
            sig.update(payload);
            if (ecdsa) {
                return sig.verify(SignatureHelper.toDER(signature));
            } else {
                return sig.verify(signature);
            }
        } catch (SignatureException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}

/**
 * Signature based Crypto implementation
 *
 * @author Paulo Lopes
 */
class CryptoSignature extends CryptoKeyPair {
    private final Signature sig;
    private final X509Certificate certificate;
    private final boolean ecdsa;

    CryptoSignature(final String algorithm, final X509Certificate certificate, final PrivateKey privateKey) {
        super(algorithm, null, privateKey);
        this.certificate = certificate;
        this.ecdsa = isECDSA(algorithm);

        Signature signature;
        try {
            // use default
            signature = Signature.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            // fallback
            try {
                signature = Signature.getInstance(certificate.getSigAlgName());
            } catch (NoSuchAlgorithmException e1) {
                // error
                throw new RuntimeException(e);
            }
        }

        this.sig = signature;
    }

    @Override
    public synchronized boolean verify(byte[] signature, byte[] payload) {
        try {
            sig.initVerify(certificate);
            sig.update(payload);
            if (ecdsa) {
                return sig.verify(SignatureHelper.toDER(signature));
            } else {
                return sig.verify(signature);
            }
        } catch (SignatureException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}

final class CryptoNone implements Crypto {
    private static final byte[] NOOP = new byte[0];

    @Override
    public String getLabel() {
        return "none";
    }

    @Override
    public byte[] sign(byte[] payload) {
        return NOOP;
    }

    @Override
    public boolean verify(byte[] signature, byte[] payload) {
        return true;
    }
}
