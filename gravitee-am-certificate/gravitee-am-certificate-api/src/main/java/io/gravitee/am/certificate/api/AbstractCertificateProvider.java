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
package io.gravitee.am.certificate.api;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.util.Base64;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.RSAKey;
import io.reactivex.Flowable;
import io.reactivex.Single;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractCertificateProvider implements CertificateProvider {
    private Date expirationDate;
    private Certificate cert;
    private JWKSet jwkSet;
    private Set<JWK> keys;
    private SignatureAlgorithm signature = SignatureAlgorithm.RS256;
    private io.gravitee.am.certificate.api.Key certificateKey;
    private List<CertificateKey> certificateKeys;

    public void createCertificateKeys(CertificateMetadata certificateMetadata) throws Exception {
        Object file = certificateMetadata.getMetadata().get(CertificateMetadata.FILE);
        Objects.requireNonNull(file, invalidCertificateFileMessage());

        try (InputStream is = new ByteArrayInputStream((byte[]) file)) {
            KeyStore keystore = keyStore();
            keystore.load(is, getStorepass().toCharArray());
            // generate JWK set
            // TODO : should be moved to the gravitee-am-jwt module
            jwkSet = JWKSet.load(keystore, name -> getKeypass().toCharArray());
            keys = getKeys();
            // generate Key pair
            java.security.Key key = keystore.getKey(getAlias(), getKeypass().toCharArray());
            if (key instanceof PrivateKey) {
                // Get certificate of public key
                cert = keystore.getCertificate(getAlias());
                // create key pair
                KeyPair keyPair = new KeyPair(cert.getPublicKey(), (PrivateKey) key);
                // create key
                certificateKey = new DefaultKey(getAlias(), keyPair);
                // update metadata
                certificateMetadata.getMetadata().put(CertificateMetadata.DIGEST_ALGORITHM_NAME, signature.getDigestName());
                // generate public certificate keys
                certificateKeys = new ArrayList<>();
                // get Signing Algorithm name
                if (cert instanceof X509Certificate) {
                    signature = getSignature(((X509Certificate) cert).getSigAlgName());
                    String pem = X509CertUtils.toPEMString((X509Certificate) cert);
                    certificateKeys.add(new CertificateKey(CertificateFormat.PEM, pem));
                    expirationDate = ((X509Certificate) cert).getNotAfter();
                }
                certificateKeys.add(new CertificateKey(CertificateFormat.SSH_RSA, RSAKeyUtils.toSSHRSAString((RSAPublicKey) keyPair.getPublic())));
            } else {
                throw new IllegalArgumentException("A RSA Signer must be supplied");
            }
        }

    }

    protected abstract String getStorepass();

    protected abstract String getAlias();

    protected abstract String getKeypass();

    protected abstract Set<String> getUse();

    protected abstract String getAlgorithm();

    protected abstract String invalidCertificateFileMessage();

    protected abstract KeyStore keyStore() throws KeyStoreException;

    @Override
    public abstract CertificateMetadata certificateMetadata();

    @Override
    public Optional<Date> getExpirationDate() {
        return Optional.ofNullable(this.expirationDate);
    }

    @Override
    public Flowable<JWK> privateKey() {
        // CertificateProvider only manage RSA key.
        com.nimbusds.jose.jwk.JWK nimbusJwk = new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey) ((KeyPair) certificateKey.getValue()).getPublic())
                .privateKey((RSAPrivateKey) ((KeyPair) certificateKey.getValue()).getPrivate())
                .keyID(getAlias())
                .build();
        return Flowable.fromIterable(convert(nimbusJwk, true).collect(Collectors.toList()));
    }

    @Override
    public Single<io.gravitee.am.certificate.api.Key> key() {
        return Single.just(certificateKey);
    }

    @Override
    public Single<String> publicKey() {
        // fallback to ssh-rsa
        return Single.just(
                certificateKeys
                        .stream()
                        .filter(c -> c.getFmt().equals(CertificateFormat.SSH_RSA))
                        .map(CertificateKey::getPayload)
                        .findFirst()
                        .orElseThrow());
    }

    @Override
    public Single<List<CertificateKey>> publicKeys() {
        return Single.just(certificateKeys);
    }

    @Override
    public Flowable<JWK> keys() {
        return Flowable.fromIterable(keys);
    }

    @Override
    public Certificate certificate() {
        return cert;
    }

    @Override
    public String signatureAlgorithm() {
        if (getAlgorithm() != null) {
            return getAlgorithm();
        }
        return signature.getValue();
    }

    private Set<JWK> getKeys() {
        return jwkSet.toPublicJWKSet().getKeys().stream().flatMap(nimbusJwk -> convert(nimbusJwk, false)).collect(Collectors.toSet());
    }

    private Stream<JWK> convert(com.nimbusds.jose.jwk.JWK nimbusJwk, boolean includePrivate) {
        final Set<String> useFor = getUse() == null || getUse().isEmpty() ? Set.of(KeyUse.SIGNATURE.getValue()) : getUse();
        return useFor.stream().map(use -> createRSAKey(nimbusJwk, includePrivate, use));
    }

    private JWK createRSAKey(com.nimbusds.jose.jwk.JWK nimbusJwk, boolean includePrivate, String use) {
        RSAKey jwk = new RSAKey();
        if (nimbusJwk.getKeyType() != null) {
            jwk.setKty(nimbusJwk.getKeyType().getValue());
        }

        if (nimbusJwk.getKeyUse() != null) {
            jwk.setUse(nimbusJwk.getKeyUse().identifier());
        } else {
            jwk.setUse(use);
        }

        if (nimbusJwk.getKeyOperations() != null) {
            jwk.setKeyOps(nimbusJwk.getKeyOperations().stream().map(KeyOperation::identifier).collect(Collectors.toSet()));
        }

        if (getAlgorithm() != null && !getAlgorithm().isEmpty()) {
            jwk.setAlg(getAlgorithm());
        } else if (nimbusJwk.getAlgorithm() != null) {
            jwk.setAlg(nimbusJwk.getAlgorithm().getName());
        }
        if (nimbusJwk.getKeyID() != null) {
            jwk.setKid(nimbusJwk.getKeyID());
        }
        if (nimbusJwk.getX509CertURL() != null) {
            jwk.setX5u(nimbusJwk.getX509CertURL().toString());
        }
        if (nimbusJwk.getX509CertChain() != null) {
            jwk.setX5c(nimbusJwk.getX509CertChain().stream().map(Base64::toString).collect(Collectors.toSet()));
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

        if (includePrivate) {
            if (nimbusRSAJwk.getPrivateExponent() != null) {
                jwk.setD(nimbusRSAJwk.getPrivateExponent().toString());
            }

            if (nimbusRSAJwk.getFirstPrimeFactor() != null) {
                jwk.setP(nimbusRSAJwk.getFirstPrimeFactor().toString());
            }

            if (nimbusRSAJwk.getFirstFactorCRTExponent() != null) {
                jwk.setDp(nimbusRSAJwk.getFirstFactorCRTExponent().toString());
            }

            if (nimbusRSAJwk.getFirstCRTCoefficient() != null) {
                jwk.setQi(nimbusRSAJwk.getFirstCRTCoefficient().toString());
            }

            if (nimbusRSAJwk.getSecondPrimeFactor() != null) {
                jwk.setQ(nimbusRSAJwk.getSecondPrimeFactor().toString());
            }

            if (nimbusRSAJwk.getSecondFactorCRTExponent() != null) {
                jwk.setDq(nimbusRSAJwk.getSecondFactorCRTExponent().toString());
            }
        }
        return jwk;
    }

    private SignatureAlgorithm getSignature(String signingAlgorithm) {
        return Stream.of(SignatureAlgorithm.values())
                .filter(signatureAlgorithm -> signatureAlgorithm.getJcaName() != null)
                .filter(signatureAlgorithm -> signatureAlgorithm.getJcaName().equals(signingAlgorithm))
                .findFirst()
                .orElse(SignatureAlgorithm.RS256);
    }
}
