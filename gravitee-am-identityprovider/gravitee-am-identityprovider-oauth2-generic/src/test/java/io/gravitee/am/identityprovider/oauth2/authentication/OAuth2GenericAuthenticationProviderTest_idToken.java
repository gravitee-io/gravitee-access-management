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
package io.gravitee.am.identityprovider.oauth2.authentication;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.JWTProcessor;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderConfiguration;
import io.reactivex.observers.TestObserver;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.text.ParseException;
import java.util.Collections;

import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class OAuth2GenericAuthenticationProviderTest_idToken {

  @InjectMocks
  private OAuth2GenericAuthenticationProvider authenticationProvider = new OAuth2GenericAuthenticationProvider();

  @Mock
  private OAuth2GenericIdentityProviderConfiguration configuration;

  @Mock
  private JWTProcessor jwtProcessor;

  @Test
  public void shouldLoadUserByUsername_authentication()
      throws ParseException, JOSEException, BadJOSEException {
    JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("bob").build();

    when(configuration.getResponseType()).thenReturn(ResponseType.ID_TOKEN);
    when(jwtProcessor.process("test", null)).thenReturn(claims);

    TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(
        new Authentication() {
          @Override
          public Object getCredentials() {
            return "__social__";
          }

          @Override
          public Object getPrincipal() {
            return "__social__";
          }

          @Override
          public AuthenticationContext getContext() {
            DummyRequest dummyRequest = new DummyRequest();
            dummyRequest.setParameters(
                Collections.singletonMap("urlHash", Collections.singletonList("#id_token=test")));
            return new DummyAuthenticationContext(
                new HashMap<>(Collections.singletonMap("id_token", "test")), dummyRequest);
          }
        }).test();

    testObserver.assertComplete();
    testObserver.assertNoErrors();
    testObserver.assertValue(u -> "bob".equals(u.getUsername()));
  }

  @Test
  public void shouldLoadUserByUsername_authentication_and_filter_undesired_claims()
      throws ParseException, JOSEException, BadJOSEException {
    JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("bob")
        .claim(Claims.nbf, "value that won't appear")
        .claim(Claims.iat, "value that won't appear")
        .claim(Claims.exp, "value that won't appear")
        .claim(Claims.updated_at, "value that won't appear")
        .claim(Claims.auth_time, "value that won't appear")
        .claim(ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY, "value that won't appear")
        .claim(ConstantKeys.OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY, "value that won't appear")
        .build();

    when(configuration.getResponseType()).thenReturn(ResponseType.ID_TOKEN);
    when(jwtProcessor.process("test", null)).thenReturn(claims);

    TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(
        new Authentication() {
          @Override
          public Object getCredentials() {
            return "__social__";
          }

          @Override
          public Object getPrincipal() {
            return "__social__";
          }

          @Override
          public AuthenticationContext getContext() {
            DummyRequest dummyRequest = new DummyRequest();
            dummyRequest.setParameters(
                Collections.singletonMap("urlHash", Collections.singletonList("#id_token=test")));
            return new DummyAuthenticationContext(
                new HashMap<>(Collections.singletonMap("id_token", "test")), dummyRequest);
          }
        }).test();

    testObserver.assertComplete();
    testObserver.assertNoErrors();
    testObserver.assertValue(u -> "bob".equals(u.getUsername()));
    testObserver.assertValue(u ->
        u.getAdditionalInformation().keySet().containsAll(List.of(
            "sub", "preferred_username"
        )));
    testObserver.assertValue(u ->
        !u.getAdditionalInformation().keySet().containsAll(List.of(
            Claims.nbf,
            Claims.iat,
            Claims.exp,
            Claims.updated_at,
            Claims.auth_time,
            ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY,
            ConstantKeys.OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY
        )));
  }

  @Test
  public void shouldLoadUserByUsername_authentication_badToken()
      throws ParseException, JOSEException, BadJOSEException {
    when(jwtProcessor.process("test", null)).thenThrow(new JOSEException("jose exception"));

    when(configuration.getResponseType()).thenReturn(ResponseType.ID_TOKEN);
    TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(
        new Authentication() {
          @Override
          public Object getCredentials() {
            return "__social__";
          }

          @Override
          public Object getPrincipal() {
            return "__social__";
          }

          @Override
          public AuthenticationContext getContext() {
            DummyRequest dummyRequest = new DummyRequest();
            dummyRequest.setParameters(
                Collections.singletonMap("urlHash", Collections.singletonList("#id_token=test")));
            return new DummyAuthenticationContext(
                new HashMap<>(Collections.singletonMap("id_token", "test")), dummyRequest);
          }
        }).test();

    testObserver.awaitTerminalEvent();
    testObserver.assertError(BadCredentialsException.class);
  }
}
