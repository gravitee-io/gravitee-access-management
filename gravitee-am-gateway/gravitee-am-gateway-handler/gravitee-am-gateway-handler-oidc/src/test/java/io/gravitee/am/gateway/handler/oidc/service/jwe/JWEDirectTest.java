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
package io.gravitee.am.gateway.handler.oidc.service.jwe;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import io.gravitee.am.common.exception.oauth2.ServerErrorException;
import io.gravitee.am.gateway.handler.oidc.service.jwe.impl.JWEServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.utils.JWAlgorithmUtils;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.jose.OCTKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.stream.Collectors;

import static org.junit.runners.Parameterized.Parameters;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(Parameterized.class)
public class JWEDirectTest {


    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @InjectMocks
    private JWEService jweService = new JWEServiceImpl();

    @Mock
    private JWKService jwkService;

    private String enc;

    public JWEDirectTest(String enc) {
        this.enc = enc;
    }

    @Parameters(name="Encrypt with Direct Encryption, enc {0}")
    public static Collection<Object[]> data() {
        return JWAlgorithmUtils.getSupportedIdTokenResponseEnc().stream().map(s -> new Object[]{s}).collect(Collectors.toList());
    }

    @Test
    public void encryptIdToken() {
        byte[] secretKey = new byte[EncryptionMethod.parse(this.enc).cekBitLength()/8];
        new SecureRandom().nextBytes(secretKey);

        // Convert to JWK format
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey).build();

        OCTKey key = new OCTKey();
        key.setKid("octEnc");
        key.setUse("enc");
        key.setK(jwk.getKeyValue().toString());

        Client client = new Client();
        client.setIdTokenEncryptedResponseAlg("dir");
        client.setIdTokenEncryptedResponseEnc(this.enc);

        when(jwkService.getKeys(client)).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.filter(any(),any())).thenReturn(Maybe.just(key));

        TestObserver testObserver = jweService.encryptIdToken("JWT", client).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jweString -> {
            JWEObject jwe = JWEObject.parse((String)jweString);
            jwe.decrypt(new DirectDecrypter(jwk));
            return "JWT".equals(jwe.getPayload().toString());
        });
    }


    @Test
    public void encryptUserinfo() {
        byte[] secretKey = new byte[EncryptionMethod.parse(this.enc).cekBitLength()/8];
        new SecureRandom().nextBytes(secretKey);

        // Convert to JWK format
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey).build();

        OCTKey key = new OCTKey();
        key.setKid("octEnc");
        key.setUse("enc");
        key.setK(jwk.getKeyValue().toString());

        Client client = new Client();
        client.setUserinfoEncryptedResponseAlg("dir");
        client.setUserinfoEncryptedResponseEnc(this.enc);

        when(jwkService.getKeys(client)).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.filter(any(),any())).thenReturn(Maybe.just(key));

        TestObserver testObserver = jweService.encryptUserinfo("JWT", client).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jweString -> {
            JWEObject jwe = JWEObject.parse((String)jweString);
            jwe.decrypt(new DirectDecrypter(jwk));
            return "JWT".equals(jwe.getPayload().toString());
        });
    }

    @Test
    public void encryptIdToken_wronkKeySize() {
        byte[] secretKey = new byte[(EncryptionMethod.parse(this.enc).cekBitLength()/8)-8];
        new SecureRandom().nextBytes(secretKey);

        // Convert to JWK format
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey).build();

        OCTKey key = new OCTKey();
        key.setKid("octEnc");
        key.setUse("enc");
        key.setK(jwk.getKeyValue().toString());

        Client client = new Client();
        client.setIdTokenEncryptedResponseAlg("dir");
        client.setIdTokenEncryptedResponseEnc(this.enc);

        when(jwkService.getKeys(client)).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.filter(any(),any())).thenReturn(Maybe.just(key));

        TestObserver testObserver = jweService.encryptIdToken("JWT", client).test();
        testObserver.assertError(ServerErrorException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void encryptUserinfo_wronkKeySize() {
        byte[] secretKey = new byte[(EncryptionMethod.parse(this.enc).cekBitLength()/8)-8];
        new SecureRandom().nextBytes(secretKey);

        // Convert to JWK format
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey).build();

        OCTKey key = new OCTKey();
        key.setKid("octEnc");
        key.setUse("enc");
        key.setK(jwk.getKeyValue().toString());

        Client client = new Client();
        client.setUserinfoEncryptedResponseAlg("dir");
        client.setUserinfoEncryptedResponseEnc(this.enc);

        when(jwkService.getKeys(client)).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.filter(any(),any())).thenReturn(Maybe.just(key));

        TestObserver testObserver = jweService.encryptUserinfo("JWT", client).test();
        testObserver.assertError(ServerErrorException.class);
        testObserver.assertNotComplete();
    }
}
