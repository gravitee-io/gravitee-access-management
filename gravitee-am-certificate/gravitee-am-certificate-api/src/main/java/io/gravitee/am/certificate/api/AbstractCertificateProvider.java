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
import io.gravitee.am.certificate.api.jwk.JwkNimbusConverter;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.RSAKey;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
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

import static io.gravitee.am.certificate.api.jwk.JwkNimbusConverter.converter;

public abstract class AbstractCertificateProvider implements CertificateProvider {
    public static final String RSA = "RSA";
    public static final String EC = "EC";
    @Autowired
    protected CertificateMetadata certificateMetadata;

    @Autowired
    protected ConfigurableApplicationContext context;

    private Date expirationDate;
    private Certificate cert;
    private JWKSet jwkSet;
    private Set<JWK> keys;
    private SignatureAlgorithm signature = SignatureAlgorithm.RS256;
    private io.gravitee.am.certificate.api.Key certificateKey;
    private List<CertificateKey> certificateKeys;

    public void createCertificateKeys(CertificateMetadata certificateMetadata) throws Exception {
        Object file = getCertificateContent(certificateMetadata);
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
                String keyId = certificateMetadata.getMetadata().get(CertificateMetadata.ID).toString();
                certificateKey = new DefaultKey(keyId, keyPair);
                // update metadata
                certificateMetadata.getMetadata().put(CertificateMetadata.DIGEST_ALGORITHM_NAME, signature.getDigestName());
                // generate public certificate keys
                certificateKeys = new ArrayList<>();
                // get Signing Algorithm name
                if (cert instanceof X509Certificate x509Certificate) {
                    signature = getSignature(x509Certificate.getSigAlgName());
                    String pem = X509CertUtils.toPEMString(x509Certificate);
                    certificateKeys.add(new CertificateKey(CertificateFormat.PEM, pem));
                    expirationDate = x509Certificate.getNotAfter();
                }
                PublicKey publicKey = keyPair.getPublic();
                if (publicKey.getAlgorithm().equals(RSA)){
                    certificateKeys.add(new CertificateKey(CertificateFormat.SSH_RSA, KeyUtils.toSSHRSAString((RSAPublicKey) publicKey)));
                } else if (publicKey.getAlgorithm().equals(EC)){
                    certificateKeys.add(new CertificateKey(CertificateFormat.ECDSA, KeyUtils.toEcdsaString((ECPublicKey) publicKey)));
                }
            } else {
                throw new IllegalArgumentException("An ECSDA or RSA Signer must be supplied");
            }
        }
    }

    protected Object getCertificateContent(CertificateMetadata certificateMetadata) {
        return certificateMetadata.getMetadata().get(CertificateMetadata.FILE);
    }

    protected abstract String getStorepass();

    protected abstract String getAlias();

    protected abstract String getKeypass();

    protected abstract Set<String> getUse();

    protected abstract String getAlgorithm();

    protected abstract String invalidCertificateFileMessage();

    protected abstract KeyStore keyStore() throws KeyStoreException;

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
        List<JWK> jwks = converter(nimbusJwk, true, getUse(), signatureAlgorithm()).createJwk().toList();
        return Flowable.fromIterable(jwks);
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
        return jwkSet.toPublicJWKSet().getKeys().stream()
                .map(nimbusJwk -> converter(nimbusJwk, false, getUse(), getAlgorithm()))
                .flatMap(JwkNimbusConverter::createJwk)
                .collect(Collectors.toSet());
    }


    private SignatureAlgorithm getSignature(String signingAlgorithm) {
        return Stream.of(SignatureAlgorithm.values())
                .filter(signatureAlgorithm -> signatureAlgorithm.getJcaName() != null)
                .filter(signatureAlgorithm -> signatureAlgorithm.getJcaName().equals(signingAlgorithm))
                .findFirst()
                .orElse(SignatureAlgorithm.RS256);
    }

    @Override
    public void unregister() {
        context.close();
    }
}
