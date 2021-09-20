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
package io.gravitee.am.gateway.handler.oidc.service.jwe.impl;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.crypto.impl.*;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.exception.oauth2.ServerErrorException;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKFilter;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.converter.JWKConverter;
import io.gravitee.am.gateway.handler.oidc.service.utils.JWAlgorithmUtils;
import io.gravitee.am.model.jose.*;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.function.Predicate;

/**
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JWEServiceImpl implements JWEService {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(JWEServiceImpl.class);

    @Autowired
    private JWKService jwkService;

    @Override
    public Single<String> encryptIdToken(String signedJwt, Client client) {
        //Return input without encryption if client does not require JWE or algorithm is set to none
        if (client.getIdTokenEncryptedResponseAlg() == null || JWEAlgorithm.NONE.getName().equalsIgnoreCase(client.getIdTokenEncryptedResponseAlg())) {
            return Single.just(signedJwt);
        }

        JWEObject jwe = new JWEObject(
                new JWEHeader.Builder(
                        JWEAlgorithm.parse(client.getIdTokenEncryptedResponseAlg()),
                        EncryptionMethod.parse(client.getIdTokenEncryptedResponseEnc()!=null?client.getIdTokenEncryptedResponseEnc(): JWAlgorithmUtils.getDefaultIdTokenResponseEnc())
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

    @Override
    public Single<String> encryptUserinfo(String signedJwt, Client client) {
        //Return input without encryption if client does not require JWE or algorithm is set to none
        if(client.getUserinfoEncryptedResponseAlg()==null || JWEAlgorithm.NONE.getName().equalsIgnoreCase(client.getUserinfoEncryptedResponseAlg())) {
            return Single.just(signedJwt);
        }

        JWEObject jwe = new JWEObject(
                new JWEHeader.Builder(
                        JWEAlgorithm.parse(client.getUserinfoEncryptedResponseAlg()),
                        EncryptionMethod.parse(client.getUserinfoEncryptedResponseEnc()!=null?client.getUserinfoEncryptedResponseEnc(): JWAlgorithmUtils.getDefaultUserinfoResponseEnc())
                ).contentType("JWT").build(),
                new Payload(signedJwt)
        );

        return encrypt(jwe,client)
                .onErrorResumeNext(throwable -> {
                    if(throwable instanceof OAuth2Exception) {
                        return Single.error(throwable);
                    }
                    LOGGER.error(throwable.getMessage(), throwable);
                    return Single.error(new ServerErrorException("Unable to encrypt userinfo"));
                });
    }

    @Override
    public Single<JWT> decrypt(String jwt) {
        return decrypt(jwt, null);
    }

    @Override
    public Single<JWT> decrypt(String jwt, Client client) {
        try {
            // Parse a first time to check if the JWT is encrypted
            JWT parsedJwt = JWTParser.parse(jwt);

            if (parsedJwt instanceof EncryptedJWT) {

                JWEObject jweObject = JWEObject.parse(jwt);

                JWEAlgorithm algorithm = jweObject.getHeader().getAlgorithm();

                //RSA decryption
                if (RSACryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
                    return decrypt(jweObject, client, JWKFilter.RSA_KEY_ENCRYPTION(), jwk ->
                            new RSADecrypter(JWKConverter.convert((RSAKey) jwk))
                    );
                }
                //Curve decryption (Elliptic "EC" & Edward "OKP")
                else if (ECDHCryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
                    return decrypt(jweObject, client, JWKFilter.CURVE_KEY_ENCRYPTION(), jwk -> {
                        if (KeyType.EC.getValue().equals(jwk.getKty())) {
                            return new ECDHDecrypter(JWKConverter.convert((ECKey) jwk));
                        }
                        return new X25519Decrypter(JWKConverter.convert((OKPKey) jwk));
                    });
                }
                //AES decryption ("OCT" keys)
                else if (AESCryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
                    return decrypt(jweObject, client, JWKFilter.OCT_KEY_ENCRYPTION(algorithm), jwk ->
                            new AESDecrypter(JWKConverter.convert((OCTKey) jwk))
                    );
                }
                //Direct decryption ("OCT" keys)
                else if (DirectCryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
                    return decrypt(jweObject, client, JWKFilter.OCT_KEY_ENCRYPTION(jweObject.getHeader().getEncryptionMethod()), jwk ->
                            new DirectDecrypter(JWKConverter.convert((OCTKey) jwk))
                    );
                }
                //Password Base decryption ("OCT" keys)
                else if (PasswordBasedCryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
                    return decrypt(jweObject, client, JWKFilter.OCT_KEY_ENCRYPTION(), jwk -> {
                        OctetSequenceKey octKey = JWKConverter.convert((OCTKey) jwk);
                        return new PasswordBasedDecrypter(octKey.getKeyValue().decode());
                    });
                }

                return Single.error(new ServerErrorException("Unable to perform Json Web Decryption, unsupported algorithm: " + algorithm.getName()));
            } else {
                return Single.just(parsedJwt);
            }
        } catch (Exception ex) {
            return Single.error(ex);
        }
    }

    private Single<JWT> decrypt(JWEObject jwe, Client client, Predicate<JWK> filter, JWEDecrypterFunction<JWK, JWEDecrypter> function) {
        final Maybe<JWKSet> jwks = client != null ? jwkService.getKeys(client) : jwkService.getDomainPrivateKeys();
        return jwks.flatMap(jwkSet -> jwkService.filter(jwkSet, filter))
                .switchIfEmpty(Maybe.error(new InvalidClientMetadataException("no matching key found to decrypt")))
                .flatMapSingle(jwk -> Single.just(function.apply(jwk)))
                .map(decrypter -> {
                    jwe.decrypt(decrypter);
                    return jwe.getPayload().toSignedJWT();
                });
    }

    public Single<String> encryptAuthorization(String signedJwt, Client client) {
        //Return input without encryption if client does not require JWE or algorithm is set to none
        if (client.getAuthorizationEncryptedResponseAlg() == null || JWEAlgorithm.NONE.getName().equals(client.getAuthorizationEncryptedResponseAlg())) {
            return Single.just(signedJwt);
        }

        JWEObject jwe = new JWEObject(
                new JWEHeader.Builder(
                        JWEAlgorithm.parse(client.getAuthorizationEncryptedResponseAlg()),
                        EncryptionMethod.parse(client.getAuthorizationEncryptedResponseEnc()!=null?client.getAuthorizationEncryptedResponseEnc(): JWAlgorithmUtils.getDefaultAuthorizationResponseEnc())
                ).contentType("JWT").build(),
                new Payload(signedJwt)
        );

        return encrypt(jwe,client)
                .onErrorResumeNext(throwable -> {
                    if(throwable instanceof OAuth2Exception) {
                        return Single.error(throwable);
                    }
                    LOGGER.error(throwable.getMessage(), throwable);
                    return Single.error(new ServerErrorException("Unable to encrypt authorization"));
                });
    }

    private Single<String> encrypt(JWEObject jwe, Client client) {

        JWEAlgorithm algorithm = jwe.getHeader().getAlgorithm();

        //RSA encryption
        if(RSACryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return encrypt(jwe, client, JWKFilter.RSA_KEY_ENCRYPTION(), jwk ->
                    new RSAEncrypter(JWKConverter.convert((RSAKey) jwk))
            );
        }
        //Curve encryption (Elliptic "EC" & Edward "OKP")
        else if(ECDHCryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return encrypt(jwe, client, JWKFilter.CURVE_KEY_ENCRYPTION(), jwk -> {
                if(KeyType.EC.getValue().equals(jwk.getKty())) {
                    return new ECDHEncrypter(JWKConverter.convert((ECKey) jwk));
                }
                return new X25519Encrypter(JWKConverter.convert((OKPKey) jwk));
            });
        }
        //AES encryption ("OCT" keys)
        else if(AESCryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return encrypt(jwe, client, JWKFilter.OCT_KEY_ENCRYPTION(algorithm), jwk ->
                    new AESEncrypter(JWKConverter.convert((OCTKey) jwk))
            );
        }
        //Direct encryption ("OCT" keys)
        else if(DirectCryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return encrypt(jwe, client, JWKFilter.OCT_KEY_ENCRYPTION(jwe.getHeader().getEncryptionMethod()), jwk ->
                    new DirectEncrypter(JWKConverter.convert((OCTKey) jwk))
            );
        }
        //Password Base Encryption ("OCT" keys)
        else if(PasswordBasedCryptoProvider.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return encrypt(jwe, client, JWKFilter.OCT_KEY_ENCRYPTION(), jwk -> {
                OctetSequenceKey octKey = JWKConverter.convert((OCTKey) jwk);
                return new PasswordBasedEncrypter(
                        octKey.getKeyValue().decode(),
                        PasswordBasedEncrypter.MIN_SALT_LENGTH,
                        PasswordBasedEncrypter.MIN_RECOMMENDED_ITERATION_COUNT
                );
            });
        }
        return Single.error(new ServerErrorException("Unable to perform Json Web Encryption, unsupported algorithm: "+algorithm.getName()));
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
    private interface JWEDecrypterFunction<JWK, JWEDecrypter> {
        JWEDecrypter apply(JWK jwk) throws JOSEException;
    }

    @FunctionalInterface
    private interface JWEEncrypterFunction<JWK, JWEEncrypter> {
        JWEEncrypter apply(JWK jwk) throws JOSEException;
    }
}
