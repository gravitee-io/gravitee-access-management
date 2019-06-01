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

import io.gravitee.am.model.Client;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(JUnit4.class)
public class GrantTypeUtilsTest {

    @Test
    public void validateGrantTypes_nullClient() {
        TestObserver<Client> testObserver = GrantTypeUtils.validateGrantTypes(null).test();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void validateGrantTypes_authorization_code_implicit_refresh_token() {
        Client client = new Client();
        client.setAuthorizedGrantTypes(Arrays.asList("authorization_code", "implicit", "refresh_token"));

        TestObserver<Client> testObserver = GrantTypeUtils.validateGrantTypes(client).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void validateGrantTypes_unknown_grant_type() {
        Client client = new Client();
        client.setAuthorizedGrantTypes(Arrays.asList("unknown"));

        TestObserver<Client> testObserver = GrantTypeUtils.validateGrantTypes(client).test();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void validateGrantTypes_empty_grant_type() {
        Client client = new Client();
        client.setAuthorizedGrantTypes(Arrays.asList());

        TestObserver<Client> testObserver = GrantTypeUtils.validateGrantTypes(client).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void validateGrantTypes_refreshToken_nok() {
        Client client = new Client();
        client.setAuthorizedGrantTypes(Arrays.asList("refresh_token","implicit"));
        client.setResponseTypes(Arrays.asList("token"));

        TestObserver<Client> testObserver = GrantTypeUtils.validateGrantTypes(client).test();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void validateGrantTypes_refreshToken_client_credentials_nok() {
        Client client = new Client();
        client.setAuthorizedGrantTypes(Arrays.asList("refresh_token","client_credentials"));
        client.setResponseTypes(Arrays.asList());

        TestObserver<Client> testObserver = GrantTypeUtils.validateGrantTypes(client).test();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void validateGrantTypes_refreshToken_ok() {
        Client client = new Client();
        client.setAuthorizedGrantTypes(Arrays.asList("refresh_token","authorization_code"));

        TestObserver<Client> testObserver = GrantTypeUtils.validateGrantTypes(client).test();

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
        Client client = new Client();
        client.setResponseTypes(Arrays.asList("code"));
        client.setAuthorizedGrantTypes(Arrays.asList());

        client = GrantTypeUtils.completeGrantTypeCorrespondance(client);
        assertTrue("was expecting code grant type",client.getAuthorizedGrantTypes().contains("authorization_code"));
    }

    @Test
    public void completeGrantTypeCorrespondance_missingImplicitGrantType() {
        Client client = new Client();
        client.setResponseTypes(Arrays.asList("id_token"));
        client.setAuthorizedGrantTypes(Arrays.asList("authorization_code"));

        client = GrantTypeUtils.completeGrantTypeCorrespondance(client);
        assertTrue("was expecting code grant type",client.getAuthorizedGrantTypes().contains("implicit"));
        assertFalse("was expecting code grant type",client.getAuthorizedGrantTypes().contains("authorization_code"));
    }

    @Test
    public void completeGrantTypeCorrespondance_removeImplicitGrantType() {
        Client client = new Client();
        client.setResponseTypes(Arrays.asList("code"));
        client.setAuthorizedGrantTypes(Arrays.asList("implicit"));

        client = GrantTypeUtils.completeGrantTypeCorrespondance(client);
        assertFalse("was expecting code grant type",client.getAuthorizedGrantTypes().contains("implicit"));
        assertTrue("was expecting code grant type",client.getAuthorizedGrantTypes().contains("authorization_code"));
    }

    @Test
    public void completeGrantTypeCorrespondance_caseNoResponseType() {
        Client client = new Client();
        client.setResponseTypes(Arrays.asList());
        client.setAuthorizedGrantTypes(Arrays.asList("client_credentials"));

        client = GrantTypeUtils.completeGrantTypeCorrespondance(client);
        assertTrue("was expecting code grant type",client.getResponseTypes().isEmpty());
        assertTrue("was expecting code grant type",client.getAuthorizedGrantTypes().contains("client_credentials"));
    }

    @Test
    public void completeGrantTypeCorrespondance_caseAllEmpty() {
        Client client = new Client();
        client.setResponseTypes(Arrays.asList());
        client.setAuthorizedGrantTypes(Arrays.asList());

        client = GrantTypeUtils.completeGrantTypeCorrespondance(client);
        assertTrue("was expecting code grant type",client.getResponseTypes().contains("code"));
        assertTrue("was expecting code grant type",client.getAuthorizedGrantTypes().contains("authorization_code"));
    }
}
