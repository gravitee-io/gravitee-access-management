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
package io.gravitee.am.service;

import io.gravitee.am.model.Client;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.TokenServiceImpl;
import io.gravitee.am.service.model.TotalToken;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class TokenServiceTest {

    @InjectMocks
    private TokenService tokenService = new TokenServiceImpl();

    @Mock
    private AccessTokenRepository accessTokenRepository;

    @Mock
    private ClientService clientService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindTotalTokensByDomain() {
        Client client1 = new Client();
        client1.setClientId("client1");
        Client client2 = new Client();
        client2.setClientId("client2");
        Set<Client> clients = new HashSet<>(Arrays.asList(client1, client2));

        AccessToken accessToken1 = new AccessToken();
        accessToken1.setId("access-1");
        accessToken1.setToken("access-1");
        AccessToken accessToken2 = new AccessToken();
        accessToken2.setId("access-2");
        accessToken2.setToken("access-2");
        AccessToken accessToken3 = new AccessToken();
        accessToken3.setId("access-3");
        accessToken3.setToken("access-3");

        when(clientService.findByDomain(DOMAIN)).thenReturn(Single.just(clients));
        when(accessTokenRepository.findByClientId("client1")).thenReturn(Observable.fromIterable(Arrays.asList(accessToken1, accessToken2)));
        when(accessTokenRepository.findByClientId("client2")).thenReturn(Observable.fromIterable(Arrays.asList(accessToken3)));

        TestObserver<TotalToken> testObserver = tokenService.findTotalTokensByDomain(DOMAIN).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(totalToken -> totalToken.getTotalAccessTokens() == 3l);
    }

    @Test
    public void shouldFindTotalTokensByDomain_technicalException() {
        when(clientService.findByDomain(DOMAIN)).thenReturn(Single.error(TechnicalException::new));

        TestObserver<TotalToken> testObserver = tokenService.findTotalTokensByDomain(DOMAIN).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindTotalTokensByDomain2_technicalException() {
        Client client1 = new Client();
        client1.setClientId("client1");
        Client client2 = new Client();
        client2.setClientId("client2");
        Set<Client> clients = new HashSet<>(Arrays.asList(client1, client2));
        when(clientService.findByDomain(DOMAIN)).thenReturn(Single.just(clients));
        when(accessTokenRepository.findByClientId("client1")).thenReturn(Observable.error(TechnicalException::new));

        TestObserver<TotalToken> testObserver = tokenService.findTotalTokensByDomain(DOMAIN).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindTotalTokens() {
        Client client1 = new Client();
        client1.setClientId("client1");
        Client client2 = new Client();
        client2.setClientId("client2");
        Set<Client> clients = new HashSet<>(Arrays.asList(client1, client2));

        AccessToken accessToken1 = new AccessToken();
        accessToken1.setId("access-1");
        accessToken1.setToken("access-1");
        AccessToken accessToken2 = new AccessToken();
        accessToken2.setId("access-2");
        accessToken2.setToken("access-2");
        AccessToken accessToken3 = new AccessToken();
        accessToken3.setId("access-3");
        accessToken3.setToken("access-3");

        when(clientService.findAll()).thenReturn(Single.just(clients));
        when(accessTokenRepository.findByClientId("client1")).thenReturn(Observable.fromIterable(Arrays.asList(accessToken1, accessToken2)));
        when(accessTokenRepository.findByClientId("client2")).thenReturn(Observable.fromIterable(Arrays.asList(accessToken3)));

        TestObserver<TotalToken> testObserver = tokenService.findTotalTokens().test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(totalToken -> totalToken.getTotalAccessTokens() == 3l);
    }

    @Test
    public void shouldFindTotalTokens_technicalException() {
        when(clientService.findAll()).thenReturn(Single.error(TechnicalException::new));

        TestObserver<TotalToken> testObserver = tokenService.findTotalTokens().test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindTotalTokens2_technicalException() {
        Client client1 = new Client();
        client1.setClientId("client1");
        Client client2 = new Client();
        client2.setClientId("client2");
        Set<Client> clients = new HashSet<>(Arrays.asList(client1, client2));
        when(clientService.findAll()).thenReturn(Single.just(clients));
        when(accessTokenRepository.findByClientId("client1")).thenReturn(Observable.error(TechnicalException::new));

        TestObserver<TotalToken> testObserver = tokenService.findTotalTokens().test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

}
