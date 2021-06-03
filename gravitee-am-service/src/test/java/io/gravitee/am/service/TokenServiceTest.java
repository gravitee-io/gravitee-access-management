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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.TokenServiceImpl;
import io.gravitee.am.service.model.TotalToken;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private ApplicationService applicationService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindTotalTokensByDomain() {
        Application app1 = new Application();
        app1.setId("app1");
        ApplicationSettings app1Settings = new ApplicationSettings();
        ApplicationOAuthSettings app1oAuthSettings = new ApplicationOAuthSettings();
        app1oAuthSettings.setClientId(app1.getId());
        app1Settings.setOauth(app1oAuthSettings);
        app1.setSettings(app1Settings);

        Application app2 = new Application();
        app2.setId("app2");
        ApplicationSettings app2Settings = new ApplicationSettings();
        ApplicationOAuthSettings app2oAuthSettings = new ApplicationOAuthSettings();
        app2oAuthSettings.setClientId(app2.getId());
        app2Settings.setOauth(app2oAuthSettings);
        app2.setSettings(app2Settings);

        Set<Application> applications = new HashSet<>(Arrays.asList(app1, app2));

        when(applicationService.findByDomain(DOMAIN)).thenReturn(Single.just(applications));
        when(accessTokenRepository.countByClientId("app1")).thenReturn(Single.just(2l));
        when(accessTokenRepository.countByClientId("app2")).thenReturn(Single.just(1l));

        TestObserver<TotalToken> testObserver = tokenService.findTotalTokensByDomain(DOMAIN).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(totalToken -> totalToken.getTotalAccessTokens() == 3l);
    }

    @Test
    public void shouldFindTotalTokensByDomain_technicalException() {
        when(applicationService.findByDomain(DOMAIN)).thenReturn(Single.error(TechnicalException::new));

        TestObserver<TotalToken> testObserver = tokenService.findTotalTokensByDomain(DOMAIN).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindTotalTokensByDomain2_technicalException() {
        Application app1 = new Application();
        app1.setId("app1");
        ApplicationSettings app1Settings = new ApplicationSettings();
        ApplicationOAuthSettings app1oAuthSettings = new ApplicationOAuthSettings();
        app1oAuthSettings.setClientId(app1.getId());
        app1Settings.setOauth(app1oAuthSettings);
        app1.setSettings(app1Settings);

        Application app2 = new Application();
        app2.setId("app2");
        ApplicationSettings app2Settings = new ApplicationSettings();
        ApplicationOAuthSettings app2oAuthSettings = new ApplicationOAuthSettings();
        app2oAuthSettings.setClientId(app2.getId());
        app2Settings.setOauth(app2oAuthSettings);
        app2.setSettings(app2Settings);

        Set<Application> applications = new HashSet<>(Arrays.asList(app1, app2));
        when(applicationService.findByDomain(DOMAIN)).thenReturn(Single.just(applications));

        TestObserver<TotalToken> testObserver = tokenService.findTotalTokensByDomain(DOMAIN).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindTotalTokens() {
        Application app1 = new Application();
        app1.setId("app1");
        ApplicationSettings app1Settings = new ApplicationSettings();
        ApplicationOAuthSettings app1oAuthSettings = new ApplicationOAuthSettings();
        app1oAuthSettings.setClientId(app1.getId());
        app1Settings.setOauth(app1oAuthSettings);
        app1.setSettings(app1Settings);

        Application app2 = new Application();
        app2.setId("app2");
        ApplicationSettings app2Settings = new ApplicationSettings();
        ApplicationOAuthSettings app2oAuthSettings = new ApplicationOAuthSettings();
        app2oAuthSettings.setClientId(app2.getId());
        app2Settings.setOauth(app2oAuthSettings);
        app2.setSettings(app2Settings);

        Set<Application> applications = new HashSet<>(Arrays.asList(app1, app2));

        when(applicationService.findAll()).thenReturn(Single.just(applications));
        when(accessTokenRepository.countByClientId("app1")).thenReturn(Single.just(2l));
        when(accessTokenRepository.countByClientId("app2")).thenReturn(Single.just(1l));

        TestObserver<TotalToken> testObserver = tokenService.findTotalTokens().test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(totalToken -> totalToken.getTotalAccessTokens() == 3l);
    }

    @Test
    public void shouldFindTotalTokens_technicalException() {
        when(applicationService.findAll()).thenReturn(Single.error(TechnicalException::new));

        TestObserver<TotalToken> testObserver = tokenService.findTotalTokens().test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindTotalTokens2_technicalException() {
        Application app1 = new Application();
        app1.setId("app1");
        ApplicationSettings app1Settings = new ApplicationSettings();
        ApplicationOAuthSettings app1oAuthSettings = new ApplicationOAuthSettings();
        app1oAuthSettings.setClientId(app1.getId());
        app1Settings.setOauth(app1oAuthSettings);
        app1.setSettings(app1Settings);

        Application app2 = new Application();
        app2.setId("app2");
        ApplicationSettings app2Settings = new ApplicationSettings();
        ApplicationOAuthSettings app2oAuthSettings = new ApplicationOAuthSettings();
        app2oAuthSettings.setClientId(app2.getId());
        app2Settings.setOauth(app2oAuthSettings);
        app2.setSettings(app2Settings);

        Set<Application> applications = new HashSet<>(Arrays.asList(app1, app2));
        when(applicationService.findAll()).thenReturn(Single.just(applications));

        TestObserver<TotalToken> testObserver = tokenService.findTotalTokens().test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDeleteTokensByUser() {
        when(accessTokenRepository.deleteByUserId("userId")).thenReturn(Completable.complete());
        when(refreshTokenRepository.deleteByUserId("userId")).thenReturn(Completable.complete());

        TestObserver testObserver = tokenService.deleteByUserId("userId").test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

}
