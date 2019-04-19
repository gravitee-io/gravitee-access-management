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
package io.gravitee.am.certificate.pkcs12.provider;

import com.nimbusds.jose.jwk.JWKSet;
import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.certificate.api.DefaultKey;
import io.gravitee.am.certificate.pkcs12.PKCS12Configuration;
import io.gravitee.am.certificate.pkcs12.Signature;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.RSAKey;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PKCS12Provider implements CertificateProvider, InitializingBean {

    private final static String KEYSTORE_TYPE = "pkcs12";

    private KeyPair keyPair;
    private JWKSet jwkSet;
    private String publicKey;
    private Set<JWK> keys;
    private Signature signature = Signature.SHA256withRSA;
    private io.gravitee.am.certificate.api.Key certificateKey;

    @Autowired
    private PKCS12Configuration configuration;

    @Autowired
    private CertificateMetadata certificateMetadata;

    @Override
    public void afterPropertiesSet() throws Exception {
        Object file = certificateMetadata.getMetadata().get(CertificateMetadata.FILE);
        Objects.requireNonNull(file, "A .p12 / .pfx  file is required to use PKCS#12 certificate");

        try (InputStream is = new ByteArrayInputStream((byte[]) file)) {
            KeyStore keystore = KeyStore.getInstance(KEYSTORE_TYPE);
            keystore.load(is, configuration.getStorepass().toCharArray());

            // generate JWK set
            jwkSet = JWKSet.load(keystore, name -> configuration.getKeypass().toCharArray());
            keys = getKeys();
            // generate Key pair
            Key key = keystore.getKey(configuration.getAlias(), configuration.getKeypass().toCharArray());
            if (key instanceof PrivateKey) {
                // Get certificate of public key
                Certificate cert = keystore.getCertificate(configuration.getAlias());
                // Get Signing Algorithm name
                if (cert instanceof X509Certificate) {
                    signature = getSignature(((X509Certificate) cert).getSigAlgOID());
                }
                certificateMetadata.getMetadata().put(CertificateMetadata.DIGEST_ALGORITHM_NAME, signature.getDigestOID());
                // Get public key
                PublicKey publicKey = cert.getPublicKey();
                // create key pair
                keyPair = new KeyPair(publicKey, (PrivateKey) key);
                // create key
                certificateKey = new DefaultKey(configuration.getAlias(), keyPair);
                // get public key
                this.publicKey = getPublicKey();
            } else {
                throw new IllegalArgumentException("A RSA Signer must be supplied");
            }
        }
    }

    @Override
    public Single<io.gravitee.am.certificate.api.Key> key() {
        return Single.just(certificateKey);
    }

    @Override
    public Single<String> publicKey() {
        return Single.just(publicKey);
    }

    @Override
    public Flowable<JWK> keys() {
        return Flowable.fromIterable(keys);
    }

    @Override
    public CertificateMetadata certificateMetadata() {
        return certificateMetadata;
    }

    private String getPublicKey() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        /* encode the "ssh-rsa" string */
        byte[] sshrsa = new byte[]{0, 0, 0, 7, 's', 's', 'h', '-', 'r', 's', 'a'};
        out.write(sshrsa);
        /* Encode the public exponent */
        BigInteger e = ((RSAPublicKey) keyPair.getPublic()).getPublicExponent();
        byte[] data = e.toByteArray();
        encodeUInt32(data.length, out);
        out.write(data);
        /* Encode the modulus */
        BigInteger m = ((RSAPublicKey) keyPair.getPublic()).getModulus();
        data = m.toByteArray();
        encodeUInt32(data.length, out);
        out.write(data);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private Set<JWK> getKeys() {
        return jwkSet.toPublicJWKSet().getKeys().stream().map(this::convert).collect(Collectors.toSet());
    }

    private void encodeUInt32(int value, OutputStream out) throws IOException {
        byte[] tmp = new byte[4];
        tmp[0] = (byte)((value >>> 24) & 0xff);
        tmp[1] = (byte)((value >>> 16) & 0xff);
        tmp[2] = (byte)((value >>> 8) & 0xff);
        tmp[3] = (byte)(value & 0xff);
        out.write(tmp);
    }

    private JWK convert(com.nimbusds.jose.jwk.JWK nimbusJwk) {
        RSAKey jwk = new RSAKey();
        if (nimbusJwk.getKeyType() != null) {
            jwk.setKty(nimbusJwk.getKeyType().getValue());
        }
        if (nimbusJwk.getKeyUse() != null) {
            jwk.setUse(nimbusJwk.getKeyUse().identifier());
        }
        if (nimbusJwk.getKeyOperations() != null) {
            jwk.setKeyOps(nimbusJwk.getKeyOperations().stream().map(keyOperation -> keyOperation.identifier()).collect(Collectors.toSet()));
        }
        if (nimbusJwk.getAlgorithm() != null) {
            jwk.setAlg(nimbusJwk.getAlgorithm().getName());
        }
        if (nimbusJwk.getKeyID() != null) {
            jwk.setKid(nimbusJwk.getKeyID());
        }
        if (nimbusJwk.getX509CertURL() != null) {
            jwk.setX5u(nimbusJwk.getX509CertURL().toString());
        }
        if (nimbusJwk.getX509CertChain() != null) {
            jwk.setX5c(nimbusJwk.getX509CertChain().stream().map(cert -> cert.toString()).collect(Collectors.toSet()));
        }
        if (nimbusJwk.getX509CertThumbprint() != null) {
            jwk.setX5t(nimbusJwk.getX509CertThumbprint().toString());
        }
        if (nimbusJwk.getX509CertSHA256Thumbprint() != null) {
            jwk.setX5tS256(nimbusJwk.getX509CertSHA256Thumbprint().toString());
        }

        // specific RSA Key
        com.nimbusds.jose.jwk.RSAKey nimbusRSAJwk = (com.nimbusds.jose.jwk.RSAKey) nimbusJwk;
        if (nimbusRSAJwk.getPublicExponent() != null) {
            jwk.setE(nimbusRSAJwk.getPublicExponent().toString());
        }
        if (nimbusRSAJwk.getModulus() != null) {
            jwk.setN(nimbusRSAJwk.getModulus().toString());
        }

        return jwk;
    }

    private Signature getSignature(String signingAlgorithmOID) {
        return Stream.of(Signature.values())
                .filter(signature -> signature.getAlgorithmId().toString().equals(signingAlgorithmOID))
                .findFirst()
                .orElse(Signature.SHA256withRSA);
    }

    @Override
    public String signatureAlgorithm() {
        return signature.getJwsAlgorithm().getName();
    }
}
