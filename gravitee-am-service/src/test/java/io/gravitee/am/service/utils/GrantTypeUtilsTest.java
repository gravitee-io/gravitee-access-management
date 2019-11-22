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
package io.gravitee.am.service.utils;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(JUnit4.class)
public class GrantTypeUtilsTest {

    @Test
    public void validateGrantTypes_nullClient() {
        TestObserver<Application> testObserver = GrantTypeUtils.validateGrantTypes(null).test();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void validateGrantTypes_authorization_code_implicit_refresh_token() {
        Application application = new Application();

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("authorization_code", "implicit", "refresh_token"));
        oAuthSettings.setResponseTypes(Arrays.asList("code"));

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);

        application.setSettings(settings);

        TestObserver<Application> testObserver = GrantTypeUtils.validateGrantTypes(application).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void validateGrantTypes_unknown_grant_type() {
        Application application = new Application();

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("unknown"));

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);

        application.setSettings(settings);

        TestObserver<Application> testObserver = GrantTypeUtils.validateGrantTypes(application).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void validateGrantTypes_empty_grant_type() {
        Application application = new Application();

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Collections.emptyList());

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);

        application.setSettings(settings);

        TestObserver<Application> testObserver = GrantTypeUtils.validateGrantTypes(application).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void validateGrantTypes_refreshToken_nok() {
        Application application = new Application();

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("refresh_token","implicit"));
        oAuthSettings.setResponseTypes(Arrays.asList("token"));

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);

        application.setSettings(settings);

        TestObserver<Application> testObserver = GrantTypeUtils.validateGrantTypes(application).test();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void validateGrantTypes_refreshToken_client_credentials_nok() {
        Application application = new Application();

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("refresh_token","client_credentials"));
        oAuthSettings.setResponseTypes(Collections.emptyList());

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);

        application.setSettings(settings);

        TestObserver<Application> testObserver = GrantTypeUtils.validateGrantTypes(application).test();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void validateGrantTypes_refreshToken_ko() {
        Application application = new Application();

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("refresh_token"));
        oAuthSettings.setResponseTypes(Collections.emptyList());

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);

        application.setSettings(settings);

        TestObserver<Application> testObserver = GrantTypeUtils.validateGrantTypes(application).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(err -> ((Throwable)err).getMessage().startsWith("refresh_token grant type must be associated with"));
        testObserver.assertNotComplete();
    }

    @Test
    public void validateGrantTypes_refreshToken_ok() {
        Application application = new Application();

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("refresh_token","authorization_code"));
        oAuthSettings.setResponseTypes(Arrays.asList("code"));

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);

        application.setSettings(settings);

        TestObserver<Application> testObserver = GrantTypeUtils.validateGrantTypes(application).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void isSupportedGrantType_empty_grant_type() {
        boolean isSupportedGrantType = GrantTypeUtils.isSupportedGrantType(Arrays.asList());
        assertFalse("Were expecting to be false",isSupportedGrantType);
    }

    @Test
    public void supportedGrantTypes() {
        assertTrue("should have at least authorization_code", GrantTypeUtils.getSupportedGrantTypes().contains("authorization_code"));
    }

    @Test
    public void isRedirectUriRequired() {
        //isRedirectUriRequired("authorization_code",true);//should be true for mobile app, false for web app...
        isRedirectUriRequired("implicit",true);
        //isRedirectUriRequired("hybrid",true);
        isRedirectUriRequired("password",false);
        isRedirectUriRequired("client_credentials",false);
    }

    private void isRedirectUriRequired(String grant, boolean expected) {
        boolean isValid = GrantTypeUtils.isRedirectUriRequired(Arrays.asList(grant));
        assertEquals("not expected result for "+grant,expected, isValid);
    }

    @Test
    public void completeGrantTypeCorrespondance_missingCodeGrantType() {
        Application application = new Application();

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Collections.emptyList());
        oAuthSettings.setResponseTypes(Arrays.asList("code"));

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);

        application.setSettings(settings);

        application = GrantTypeUtils.completeGrantTypeCorrespondance(application);
        assertTrue("was expecting code grant type",application.getSettings().getOauth().getGrantTypes().contains("authorization_code"));
    }

    @Test
    public void completeGrantTypeCorrespondance_missingImplicitGrantType() {
        Application application = new Application();

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("authorization_code"));
        oAuthSettings.setResponseTypes(Arrays.asList("id_token"));

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);

        application.setSettings(settings);

        application = GrantTypeUtils.completeGrantTypeCorrespondance(application);
        assertTrue("was expecting code grant type",application.getSettings().getOauth().getGrantTypes().contains("implicit"));
        assertFalse("was expecting code grant type",application.getSettings().getOauth().getGrantTypes().contains("authorization_code"));
    }

    @Test
    public void completeGrantTypeCorrespondance_removeImplicitGrantType() {
        Application application = new Application();

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("implicit"));
        oAuthSettings.setResponseTypes(Arrays.asList("code"));

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);

        application.setSettings(settings);

        application = GrantTypeUtils.completeGrantTypeCorrespondance(application);
        assertFalse("was expecting code grant type",application.getSettings().getOauth().getGrantTypes().contains("implicit"));
        assertTrue("was expecting code grant type",application.getSettings().getOauth().getGrantTypes().contains("authorization_code"));
    }

    @Test
    public void completeGrantTypeCorrespondance_caseNoResponseType() {
        Application application = new Application();

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Arrays.asList("client_credentials"));
        oAuthSettings.setResponseTypes(Collections.emptyList());

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);

        application.setSettings(settings);

        application = GrantTypeUtils.completeGrantTypeCorrespondance(application);
        assertTrue("was expecting code grant type",application.getSettings().getOauth().getResponseTypes().isEmpty());
        assertTrue("was expecting code grant type",application.getSettings().getOauth().getGrantTypes().contains("client_credentials"));
    }

    @Test
    public void completeGrantTypeCorrespondance_caseAllEmpty() {
        Application application = new Application();

        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setGrantTypes(Collections.emptyList());
        oAuthSettings.setResponseTypes(Collections.emptyList());

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oAuthSettings);

        application.setSettings(settings);

        application = GrantTypeUtils.completeGrantTypeCorrespondance(application);
        assertTrue("was expecting code grant type",application.getSettings().getOauth().getResponseTypes().contains("code"));
        assertTrue("was expecting code grant type",application.getSettings().getOauth().getGrantTypes().contains("authorization_code"));
    }
}
