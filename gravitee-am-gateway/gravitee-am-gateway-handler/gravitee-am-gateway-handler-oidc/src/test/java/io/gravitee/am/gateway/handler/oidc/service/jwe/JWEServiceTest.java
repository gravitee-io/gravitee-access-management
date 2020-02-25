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

import io.gravitee.am.common.exception.oauth2.ServerErrorException;
import io.gravitee.am.gateway.handler.oidc.service.jwe.impl.JWEServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.OCTKey;
import io.gravitee.am.model.jose.OKPKey;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class JWEServiceTest {

    private static final String JWT = "JWT";

    @InjectMocks
    private JWEService jweService = new JWEServiceImpl();

    @Mock
    private JWKService jwkService;

    @Test
    public void encryptUserinfo_noEncryption() {
        String jwt = "JWT";
        TestObserver testObserver = jweService.encryptUserinfo(jwt, new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult(jwt);
    }

    @Test
    public void encryptUserinfo_unknownAlg() {
        Client client = new Client();
        client.setUserinfoEncryptedResponseAlg("unknown");
        TestObserver testObserver = jweService.encryptUserinfo(JWT, client).test();
        testObserver.assertError(ServerErrorException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void encryptIdToken_noEncryption() {
        String jwt = "JWT";
        TestObserver testObserver = jweService.encryptIdToken(jwt, new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult(jwt);
    }

    @Test
    public void encryptIdToken_unknownAlg() {
        Client client = new Client();
        client.setIdTokenEncryptedResponseAlg("unknown");
        TestObserver testObserver = jweService.encryptIdToken(JWT, client).test();
        testObserver.assertError(ServerErrorException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void encryptIdToken_RSA_noMatchingKeys() {
        encryptIdToken_noMatchingKeys("RSA-OAEP-256");
    }

    @Test
    public void encryptIdToken_EC_noMatchingKeys() {
        encryptIdToken_noMatchingKeys("ECDH-ES");
    }

    @Test
    public void encryptIdToken_AES_noMatchingKeys() {
        encryptIdToken_noMatchingKeys("A128KW");
    }

    @Test
    public void encryptIdToken_DIR_noMatchingKeys() {
        encryptIdToken_noMatchingKeys("dir");
    }

    @Test
    public void encryptIdToken_PBE_noMatchingKeys() {
        encryptIdToken_noMatchingKeys("PBES2-HS512+A256KW");
    }

    private void encryptIdToken_noMatchingKeys(String alg) {
        Client client = new Client();
        client.setIdTokenEncryptedResponseAlg(alg);

        when(jwkService.getKeys(client)).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.filter(any(),any())).thenReturn(Maybe.empty());

        TestObserver testObserver = jweService.encryptIdToken("JWT", client).test();
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void encryptIdToken_RSA_parseError() {
        RSAKey key = new RSAKey();
        key.setE("e");
        key.setN("n");

        encryptIdToken_parseError(key,"RSA-OAEP-256");
    }

    @Test
    public void encryptIdToken_EC_parseError() {
        ECKey key = new ECKey();
        key.setCrv("crv");
        key.setX("x");
        key.setY("y");
        encryptIdToken_parseError(key,"ECDH-ES");
    }

    @Test
    public void encryptIdToken_OKP_parseError() {
        OKPKey key = new OKPKey();
        key.setCrv("crv");
        key.setX("x");
        encryptIdToken_parseError(key,"ECDH-ES");
    }

    @Test
    public void encryptIdToken_OCT_parseError() {
        OCTKey key = new OCTKey();
        key.setK("k");
        encryptIdToken_parseError(key,"dir");
    }

    public void encryptIdToken_parseError(JWK jwk, String alg) {
        jwk.setUse("");//will throw ParseException

        Client client = new Client();
        client.setIdTokenEncryptedResponseAlg(alg);

        when(jwkService.getKeys(client)).thenReturn(Maybe.just(new JWKSet()));
        when(jwkService.filter(any(),any())).thenReturn(Maybe.just(jwk));

        TestObserver testObserver = jweService.encryptIdToken("JWT", client).test();
        testObserver.assertError(ServerErrorException.class);
        testObserver.assertNotComplete();
    }
}
