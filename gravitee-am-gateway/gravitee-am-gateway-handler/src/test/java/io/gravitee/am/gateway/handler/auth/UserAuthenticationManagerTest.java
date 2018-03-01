package io.gravitee.am.gateway.handler.auth;

import io.gravitee.am.gateway.handler.auth.exception.BadCredentialsException;
import io.gravitee.am.gateway.handler.auth.impl.UserAuthenticationManagerImpl;
import io.gravitee.am.gateway.handler.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.model.Client;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserAuthenticationManagerTest {

    private UserAuthenticationManagerImpl userAuthenticationManager = new UserAuthenticationManagerImpl();

    @Mock
    private ClientService clientService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Before
    public void init() {
        initMocks(this);
        userAuthenticationManager.setClientService(clientService);
        userAuthenticationManager.setIdentityProviderManager(identityProviderManager);
    }

    @Test
    public void shouldNotAuthenticateUser() {
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(new Client()));

        TestObserver<String> observer = new TestObserver<>();

        userAuthenticationManager.authenticate("client-id", new Authentication() {
            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return null;
            }

            @Override
            public Map<String, Object> getAdditionalInformation() {
                return null;
            }
        });

        observer.assertError(BadCredentialsException.class);
    }
}
