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

import static com.nimbusds.jose.JWEAlgorithm.*;
import static org.junit.runners.Parameterized.Parameters;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.PasswordBasedDecrypter;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import io.gravitee.am.gateway.handler.oidc.service.jwe.impl.JWEServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.utils.JWAlgorithmUtils;
import io.gravitee.am.model.jose.OCTKey;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(Parameterized.class)
public class JWEPbeTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @InjectMocks
    private JWEService jweService = new JWEServiceImpl();

    @Mock
    private JWKService jwkService;

    private String alg;
    private String enc;

    public JWEPbeTest(String alg, String enc) {
        this.alg = alg;
        this.enc = enc;
    }

    @Parameters(name = "Encrypt with AES alg {0}, enc {1}")
    public static Collection<Object[]> data() {
        //return list of Object[]{Algorithm, Encryption}
        return Arrays
            .asList(PBES2_HS256_A128KW, PBES2_HS384_A192KW, PBES2_HS512_A256KW)
            .stream()
            .flatMap(
                algorithm ->
                    JWAlgorithmUtils.getSupportedIdTokenResponseEnc().stream().map(enc -> new Object[] { algorithm.getName(), enc })
            )
            .collect(Collectors.toList());
    }

    @Test
    public void encryptIdToken() {
        // Convert to JWK format
        OctetSequenceKey jwk = new OctetSequenceKey.Builder("secret".getBytes()).build();

        OCTKey key = new OCTKey();
        key.setKid("octEnc");
        key.setUse("enc");
        key.setK(jwk.getKeyValue().toString());

        Client client = new Client();
        client.setIdTokenEncryptedResponseAlg(this.alg);
        client.setIdTokenEncryptedResponseEnc(this.enc);

        when(jwkService.getKeys(client)).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.filter(any(), any())).thenReturn(Maybe.just(key));

        TestObserver testObserver = jweService.encryptIdToken("JWT", client).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(
            jweString -> {
                JWEObject jwe = JWEObject.parse((String) jweString);
                jwe.decrypt(new PasswordBasedDecrypter(jwk.getKeyValue().decode()));
                return "JWT".equals(jwe.getPayload().toString());
            }
        );
    }

    @Test
    public void encryptUserinfo() {
        // Convert to JWK format
        OctetSequenceKey jwk = new OctetSequenceKey.Builder("secret".getBytes()).build();

        OCTKey key = new OCTKey();
        key.setKid("octEnc");
        key.setUse("enc");
        key.setK(jwk.getKeyValue().toString());

        Client client = new Client();
        client.setUserinfoEncryptedResponseAlg(this.alg);
        client.setUserinfoEncryptedResponseEnc(this.enc);

        when(jwkService.getKeys(client)).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.filter(any(), any())).thenReturn(Maybe.just(key));

        TestObserver testObserver = jweService.encryptUserinfo("JWT", client).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(
            jweString -> {
                JWEObject jwe = JWEObject.parse((String) jweString);
                jwe.decrypt(new PasswordBasedDecrypter(jwk.getKeyValue().decode()));
                return "JWT".equals(jwe.getPayload().toString());
            }
        );
    }
}
