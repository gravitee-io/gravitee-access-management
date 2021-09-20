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
package io.gravitee.am.gateway.handler.oauth2.service.request;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.oauth2.InvalidRequestObjectException;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.gravitee.am.gateway.handler.oidc.service.request.impl.RequestObjectServiceImpl;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import net.minidev.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.text.ParseException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RequestObjectServiceTest {

    @InjectMocks
    private RequestObjectService requestObjectService = new RequestObjectServiceImpl();

    @Mock
    private JWEService jweService;

    @Test
    public void shouldNotReadRequestObject_plainJwt() {
        Client client = new Client();
        String request = "request-object";
        PlainJWT plainJWT = mock(PlainJWT.class);;

        when(jweService.decrypt(request, false)).thenReturn(Single.just(plainJWT));

        TestObserver<JWT> testObserver = requestObjectService.readRequestObject(request, client, false).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestObjectException.class);
    }

    @Test
    public void shouldNotReadRequestObject_algo_none() throws ParseException {
        Client client = new Client();
        String request = "request-object";
        JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.parse("NONE"));
        JSONObject jsonObject = new JSONObject();
        SignedJWT signedJWT = new SignedJWT(jwsHeader,  JWTClaimsSet.parse(jsonObject));

        when(jweService.decrypt(request, false)).thenReturn(Single.just(signedJWT));

        TestObserver<JWT> testObserver = requestObjectService.readRequestObject(request, client, false).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestObjectException.class);
    }
}
