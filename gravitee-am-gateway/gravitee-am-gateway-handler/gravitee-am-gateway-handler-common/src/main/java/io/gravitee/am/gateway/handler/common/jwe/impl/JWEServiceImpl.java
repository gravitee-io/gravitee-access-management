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
package io.gravitee.am.gateway.handler.common.jwe.impl;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.AESEncrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.PasswordBasedEncrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.X25519Encrypter;
import com.nimbusds.jose.crypto.impl.AESCryptoProvider;
import com.nimbusds.jose.crypto.impl.DirectCryptoProvider;
import com.nimbusds.jose.crypto.impl.ECDHCryptoProvider;
import com.nimbusds.jose.crypto.impl.PasswordBasedCryptoProvider;
import com.nimbusds.jose.crypto.impl.RSACryptoProvider;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import io.gravitee.am.common.oauth2.exception.OAuth2Exception;
import io.gravitee.am.common.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.common.jwe.JWEService;
import io.gravitee.am.gateway.handler.common.jwk.JWKService;
import io.gravitee.am.gateway.handler.common.jwk.converter.JWKConverter;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.OCTKey;
import io.gravitee.am.model.jose.OKPKey;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.function.Predicate;

import static io.gravitee.am.gateway.handler.common.jwa.utils.JWAlgorithmUtils.getDefaultIdTokenResponseEnc;
import static io.gravitee.am.gateway.handler.common.jwk.JWKFilter.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JWEServiceImpl implements JWEService {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(JWEServiceImpl.class);

    @Autowired
    JWKService jwkService;

    @Override
    public Single<String> encryptIdToken(String signedJwt, Client client) {
        //Return input without encryption if client does not require JWE or algorithm is set to none
        if(client.getIdTokenEncryptedResponseAlg()==null || JWEAlgorithm.NONE.equals(client.getIdTokenEncryptedResponseAlg())) {
            return Single.just(signedJwt);
        }

        JWEObject jwe = new JWEObject(
                new JWEHeader.Builder(
                        JWEAlgorithm.parse(client.getIdTokenEncryptedResponseAlg()),
                        EncryptionMethod.parse(client.getIdTokenEncryptedResponseEnc()!=null?client.getIdTokenEncryptedResponseEnc():getDefaultIdTokenResponseEnc())
                ).contentType("JWT").build(),
                new Payload(signedJwt)
        );

        return encrypt(jwe,client)
                .onErrorResumeNext(throwable -> {
                    if(throwable instanceof OAuth2Exception) {
                        return Single.error(throwable);
                    }
                    LOGGER.error(throwable.getMessage(), throwable);
                    return Single.error(new ServerErrorException("Unable to encrypt id_token"));
                });
    }

    private Single<String> encrypt(JWEObject jwe, Client client) {

        JWEAlgorithm algorithm = jwe.getHeader().getAlgorithm();

        //RSA encryption
        if(RSACryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return encrypt(jwe, client, RSA_KEY_ENCRYPTION(), jwk ->
                    new RSAEncrypter(JWKConverter.convert((RSAKey) jwk))
            );
        }
        //Curve encryption (Elliptic "EC" & Edward "OKP")
        else if(ECDHCryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return encrypt(jwe, client, CURVE_KEY_ENCRYPTION(), jwk -> {
                if(KeyType.EC.getValue().equals(jwk.getKty())) {
                    return new ECDHEncrypter(JWKConverter.convert((ECKey) jwk));
                }
                return new X25519Encrypter(JWKConverter.convert((OKPKey) jwk));
            });
        }
        //AES encryption ("OCT" keys)
        else if(AESCryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return encrypt(jwe, client, OCT_KEY_ENCRYPTION(algorithm), jwk ->
                    new AESEncrypter(JWKConverter.convert((OCTKey) jwk))
            );
        }
        //Direct encryption ("OCT" keys)
        else if(DirectCryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return encrypt(jwe, client, OCT_KEY_ENCRYPTION(jwe.getHeader().getEncryptionMethod()), jwk ->
                    new DirectEncrypter(JWKConverter.convert((OCTKey) jwk))
            );
        }
        //Password Base Encryption ("OCT" keys)
        else if(PasswordBasedCryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return encrypt(jwe, client, OCT_KEY_ENCRYPTION(), jwk -> {
                OctetSequenceKey octKey = JWKConverter.convert((OCTKey) jwk);
                return new PasswordBasedEncrypter(
                        octKey.getKeyValue().decode(),
                        PasswordBasedEncrypter.MIN_SALT_LENGTH,
                        PasswordBasedEncrypter.MIN_RECOMMENDED_ITERATION_COUNT
                );
            });
        }
        return Single.error(new ServerErrorException("Unable to perform Json Web Encryption, unsupported algorithm"+algorithm.getName()));
    }

    private Single<String> encrypt(JWEObject jwe, Client client, Predicate<JWK> filter, JWEEncrypterFunction<JWK, JWEEncrypter> function) {
        return jwkService.getKeys(client)
                .flatMap(jwkSet -> jwkService.filter(jwkSet, filter))
                .switchIfEmpty(Maybe.error(new InvalidClientMetadataException("no matching key found to encrypt")))
                .flatMapSingle(jwk -> Single.just(function.apply(jwk)))
                .map(encrypter -> {
                    jwe.encrypt(encrypter);
                    return jwe.serialize();
                });
    }

    @FunctionalInterface
    private interface JWEEncrypterFunction<JWK, JWEEncrypter> {
        JWEEncrypter apply(JWK jwk) throws JOSEException;
    }
}
