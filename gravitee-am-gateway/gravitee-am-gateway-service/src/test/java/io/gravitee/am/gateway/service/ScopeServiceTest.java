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
package io.gravitee.am.gateway.service;

import io.gravitee.am.gateway.service.exception.ScopeNotFoundException;
import io.gravitee.am.gateway.service.exception.TechnicalManagementException;
import io.gravitee.am.gateway.service.impl.ScopeServiceImpl;
import io.gravitee.am.gateway.service.model.NewScope;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ScopeRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ScopeServiceTest {

    @InjectMocks
    private ScopeService scopeService = new ScopeServiceImpl();

    @Mock
    private ScopeRepository scopeRepository;

    @Mock
    private RoleService roleService;

    @Mock
    private ClientService clientService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() throws TechnicalException {
        when(scopeRepository.findById("my-scope")).thenReturn(Optional.of(new Scope()));

        Scope scope = scopeService.findById("my-scope");
        Assert.assertNotNull(scope);
    }

    @Test(expected = ScopeNotFoundException.class)
    public void shouldFindById_notExistingScope() throws TechnicalException {
        when(scopeRepository.findById("my-scope")).thenReturn(Optional.empty());

        scopeService.findById("my-scope");
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldFindById_technicalException() throws TechnicalException {
        when(scopeRepository.findById("my-scope")).thenThrow(TechnicalException.class);

        scopeService.findById("my-scope");
    }

    @Test
    public void shouldCreateScope() throws TechnicalException {
        NewScope newScope = Mockito.mock(NewScope.class);
        when(newScope.getKey()).thenReturn("my-scope");
        when(scopeRepository.findByDomainAndKey(DOMAIN, "my-scope")).thenReturn(Optional.empty());

        scopeService.create(DOMAIN, newScope);

        verify(scopeRepository, times(1)).create(any(Scope.class));
    }

    @Test
    public void shouldCreateScope_keyLowerCase() throws TechnicalException {
        NewScope newScope = Mockito.mock(NewScope.class);
        when(newScope.getKey()).thenReturn("MY-SCOPE");
        when(scopeRepository.findByDomainAndKey(DOMAIN, "my-scope")).thenReturn(Optional.empty());

        scopeService.create(DOMAIN, newScope);

        verify(scopeRepository, times(1)).create(any(Scope.class));
        verify(scopeRepository, times(1)).create(argThat(new ArgumentMatcher<Scope>() {
            @Override
            public boolean matches(Object argument) {
                Scope scope = (Scope) argument;
                return scope.getKey().equals("my-scope");
            }
        }));
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldNotCreateScope_technicalException() throws TechnicalException {
        NewScope newScope = Mockito.mock(NewScope.class);
        when(newScope.getKey()).thenReturn("my-scope");
        when(scopeRepository.findByDomainAndKey(DOMAIN, "my-scope")).thenThrow(TechnicalException.class);

        scopeService.create(DOMAIN, newScope);

        verify(scopeRepository, never()).create(any(Scope.class));
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldNotCreateScope_existingScope() throws TechnicalException {
        NewScope newScope = Mockito.mock(NewScope.class);
        when(newScope.getKey()).thenReturn("my-scope");
        when(scopeRepository.findByDomainAndKey(DOMAIN, "my-scope")).thenReturn(Optional.of(new Scope()));

        scopeService.create(DOMAIN, newScope);

        verify(scopeRepository, never()).create(any(Scope.class));
    }

    @Test(expected = ScopeNotFoundException.class)
    public void shouldDeleteScope_notExistingScope() throws TechnicalException {
        when(scopeRepository.findById("my-scope")).thenReturn(Optional.empty());

        scopeService.delete("my-scope");
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldDeleteScope_technicalException() throws TechnicalException {
        when(scopeRepository.findById("my-scope")).thenReturn(Optional.of(new Scope()));
        doThrow(TechnicalException.class).when(scopeRepository).delete("my-scope");

        scopeService.delete("my-scope");
    }

    @Test
    public void shouldDeleteScope() throws TechnicalException {
        when(scopeRepository.findById("my-scope")).thenReturn(Optional.of(new Scope()));

        scopeService.delete("my-scope");

        verify(scopeRepository, times(1)).delete("my-scope");
    }
}
