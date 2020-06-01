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
import io.gravitee.am.model.Role;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ScopeRepository;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.impl.ScopeServiceImpl;
import io.gravitee.am.service.model.*;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ScopeServiceTest {

    @InjectMocks
    private ScopeService scopeService = new ScopeServiceImpl();

    @Mock
    private RoleService roleService;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private ScopeRepository scopeRepository;

    @Mock
    private ScopeApprovalRepository scopeApprovalRepository;

    @Mock
    private EventService eventService;

    @Mock
    private AuditService auditService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(scopeRepository.findById("my-scope")).thenReturn(Maybe.just(new Scope()));
        TestObserver testObserver = scopeService.findById("my-scope").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingScope() {
        when(scopeRepository.findById("my-scope")).thenReturn(Maybe.empty());
        TestObserver testObserver = scopeService.findById("my-scope").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(scopeRepository.findById("my-scope")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        scopeService.findById("my-scope").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomain() {
        when(scopeRepository.findByDomain(DOMAIN)).thenReturn(Single.just(Collections.singleton(new Scope())));
        TestObserver<Set<Scope>> testObserver = scopeService.findByDomain(DOMAIN).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopes -> scopes.size() == 1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(scopeRepository.findByDomain(DOMAIN)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        scopeService.findByDomain(DOMAIN).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainAndKey_technicalException() {
        when(scopeRepository.findByDomainAndKey(DOMAIN, "my-scope")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver<Scope> testObserver = scopeService.findByDomainAndKey(DOMAIN, "my-scope").test();
        testObserver.assertNotComplete().assertError(TechnicalManagementException.class);
    }

    @Test
    public void shouldFindByDomainAndKey() {
        when(scopeRepository.findByDomainAndKey(DOMAIN, "my-scope")).thenReturn(Maybe.just(new Scope()));
        TestObserver<Scope> testObserver = scopeService.findByDomainAndKey(DOMAIN, "my-scope").test();
        testObserver.assertComplete().assertNoErrors().assertValue(Objects::nonNull);
    }

    @Test
    public void shouldFindByDomainAndKeys_nullInput() {
        TestObserver<List<Scope>> testObserver = scopeService.findByDomainAndKeys(DOMAIN, null).test();
        testObserver.assertComplete().assertNoErrors().assertValue(List::isEmpty);
    }

    @Test
    public void shouldFindByDomainAndKeys_emptyInput() {
        TestObserver<List<Scope>> testObserver = scopeService.findByDomainAndKeys(DOMAIN, Collections.emptyList()).test();
        testObserver.assertComplete().assertNoErrors().assertValue(List::isEmpty);
    }

    @Test
    public void shouldFindByDomainAndKeys_technicalException() {
        List<String> searchingScopes = Arrays.asList("a","b");
        when(scopeRepository.findByDomainAndKeys(DOMAIN, searchingScopes)).thenReturn(Single.error(TechnicalException::new));
        TestObserver<List<Scope>> testObserver = scopeService.findByDomainAndKeys(DOMAIN, searchingScopes).test();
        testObserver.assertNotComplete().assertError(TechnicalManagementException.class);
    }

    @Test
    public void shouldFindByDomainAndKeys() {
        List<String> searchingScopes = Arrays.asList("a","b");
        when(scopeRepository.findByDomainAndKeys(DOMAIN, searchingScopes)).thenReturn(Single.just(Arrays.asList(new Scope())));
        TestObserver<List<Scope>> testObserver = scopeService.findByDomainAndKeys(DOMAIN, searchingScopes).test();
        testObserver.assertComplete().assertNoErrors().assertValue(scopes -> scopes.size()==1);
    }

    @Test
    public void shouldCreate() {
        NewScope newScope = Mockito.mock(NewScope.class);
        when(newScope.getKey()).thenReturn("my-scope");
        when(newScope.getIconUri()).thenReturn("https://gravitee.io/icon");
        when(scopeRepository.findByDomainAndKey(DOMAIN, "my-scope")).thenReturn(Maybe.empty());
        when(scopeRepository.create(any(Scope.class))).thenReturn(Single.just(new Scope()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = scopeService.create(DOMAIN, newScope).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(scopeRepository, times(1)).findByDomainAndKey(anyString(), anyString());
        verify(scopeRepository, times(1)).create(any(Scope.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldCreate_keyUpperCase() {
        NewScope newScope = Mockito.mock(NewScope.class);
        when(newScope.getKey()).thenReturn("MY-SCOPE");
        when(scopeRepository.findByDomainAndKey(DOMAIN, "MY-SCOPE")).thenReturn(Maybe.empty());
        when(scopeRepository.create(any(Scope.class))).thenReturn(Single.just(new Scope()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = scopeService.create(DOMAIN, newScope).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(scopeRepository, times(1)).create(any(Scope.class));
        verify(scopeRepository, times(1)).create(argThat(new ArgumentMatcher<Scope>() {
            @Override
            public boolean matches(Scope scope) {
                return scope.getKey().equals("MY-SCOPE");
            }
        }));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldCreate_whiteSpaces() {
        NewScope newScope = Mockito.mock(NewScope.class);
        when(newScope.getKey()).thenReturn("MY scope");
        when(scopeRepository.findByDomainAndKey(DOMAIN, "MY_scope")).thenReturn(Maybe.empty());
        when(scopeRepository.create(any(Scope.class))).thenReturn(Single.just(new Scope()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = scopeService.create(DOMAIN, newScope).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(scopeRepository, times(1)).create(any(Scope.class));
        verify(scopeRepository, times(1)).create(argThat(new ArgumentMatcher<Scope>() {
            @Override
            public boolean matches(Scope scope) {
                return scope.getKey().equals("MY_scope");
            }
        }));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldNotCreate_malformedIconUri() {
        NewScope newScope = Mockito.mock(NewScope.class);
        when(newScope.getKey()).thenReturn("my-scope");
        when(newScope.getIconUri()).thenReturn("malformedIconUri");
        when(scopeRepository.findByDomainAndKey(DOMAIN, "my-scope")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        scopeService.create(DOMAIN, newScope).subscribe(testObserver);

        testObserver.assertError(MalformedIconUriException.class);
        testObserver.assertNotComplete();

        verify(scopeRepository, times(1)).findByDomainAndKey(DOMAIN,"my-scope");
        verify(scopeRepository, never()).create(any(Scope.class));
    }

    @Test
    public void shouldNotCreate_technicalException() {
        NewScope newScope = Mockito.mock(NewScope.class);
        when(newScope.getKey()).thenReturn("my-scope");
        when(scopeRepository.findByDomainAndKey(DOMAIN, "my-scope")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        scopeService.create(DOMAIN, newScope).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(scopeRepository, never()).create(any(Scope.class));
    }

    @Test
    public void shouldNotCreate_existingScope() {
        NewScope newScope = Mockito.mock(NewScope.class);
        when(newScope.getKey()).thenReturn("my-scope");
        when(scopeRepository.findByDomainAndKey(DOMAIN, "my-scope")).thenReturn(Maybe.just(new Scope()));

        TestObserver testObserver = new TestObserver();
        scopeService.create(DOMAIN, newScope).subscribe(testObserver);

        testObserver.assertError(ScopeAlreadyExistsException.class);
        testObserver.assertNotComplete();

        verify(scopeRepository, never()).create(any(Scope.class));
    }

    @Test
    public void shouldPatch_systemScope_discoveryNotReplaced() {
        PatchScope patch = new PatchScope();
        patch.setDiscovery(Optional.of(true));
        patch.setName(Optional.of("name"));

        final String scopeId = "toPatchId";

        Scope toPatch = new Scope();
        toPatch.setId(scopeId);
        toPatch.setSystem(true);
        toPatch.setDiscovery(false);
        toPatch.setName("oldName");
        toPatch.setDescription("oldDescription");

        ArgumentCaptor<Scope> argument = ArgumentCaptor.forClass(Scope.class);

        when(scopeRepository.findById(scopeId)).thenReturn(Maybe.just(toPatch));
        when(scopeRepository.update(argument.capture())).thenReturn(Single.just(new Scope()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = scopeService.patch(DOMAIN,scopeId, patch).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(scopeRepository, times(1)).update(any(Scope.class));
        assertNotNull(argument.getValue());
        assertEquals("name",argument.getValue().getName());
        assertEquals("oldDescription",argument.getValue().getDescription());
        assertFalse(argument.getValue().isDiscovery());
    }

    @Test
    public void shouldPatch_nonSystemScope_discoveryNotReplaced() {
        PatchScope patch = new PatchScope();
        patch.setDiscovery(Optional.of(true));
        patch.setName(Optional.of("name"));

        final String scopeId = "toPatchId";

        Scope toPatch = new Scope();
        toPatch.setId(scopeId);
        toPatch.setSystem(false);
        toPatch.setDiscovery(false);
        toPatch.setName("oldName");
        toPatch.setDescription("oldDescription");

        ArgumentCaptor<Scope> argument = ArgumentCaptor.forClass(Scope.class);

        when(scopeRepository.findById(scopeId)).thenReturn(Maybe.just(toPatch));
        when(scopeRepository.update(argument.capture())).thenReturn(Single.just(new Scope()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = scopeService.patch(DOMAIN,scopeId, patch).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(scopeRepository, times(1)).update(any(Scope.class));
        assertNotNull(argument.getValue());
        assertEquals("name",argument.getValue().getName());
        assertEquals("oldDescription",argument.getValue().getDescription());
        assertTrue(argument.getValue().isDiscovery());
    }

    @Test
    public void shouldNotPatch() {
        Scope toPatch = new Scope();
        toPatch.setId("toPatchId");

        when(scopeRepository.findById("toPatchId")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = scopeService.patch(DOMAIN,"toPatchId", new PatchScope()).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldNotPatch_scopeNotFound() {
        PatchScope patchScope = new PatchScope();
        when(scopeRepository.findById("my-scope")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        scopeService.patch(DOMAIN, "my-scope",patchScope).subscribe(testObserver);

        testObserver.assertError(ScopeNotFoundException.class);
        testObserver.assertNotComplete();

        verify(scopeRepository, times(1)).findById("my-scope");
        verify(scopeRepository, never()).update(any(Scope.class));
    }

    @Test
    public void shouldNotPatch_malformedIconUri() {
        PatchScope patchScope = new PatchScope();
        patchScope.setIconUri(Optional.of("malformedIconUri"));
        when(scopeRepository.findById("my-scope")).thenReturn(Maybe.just(new Scope()));

        TestObserver testObserver = new TestObserver();
        scopeService.patch(DOMAIN, "my-scope",patchScope).subscribe(testObserver);

        testObserver.assertError(MalformedIconUriException.class);
        testObserver.assertNotComplete();

        verify(scopeRepository, times(1)).findById("my-scope");
        verify(scopeRepository, never()).update(any(Scope.class));
    }

    @Test
    public void shouldUpdate_systemScope_discoveryNotReplaced() {
        UpdateScope updateScope = new UpdateScope();
        updateScope.setDiscovery(true);
        updateScope.setName("name");

        final String scopeId = "toUpdateId";

        Scope toUpdate = new Scope();
        toUpdate.setId(scopeId);
        toUpdate.setSystem(true);
        toUpdate.setDiscovery(false);
        toUpdate.setName("oldName");
        toUpdate.setDescription("oldDescription");

        ArgumentCaptor<Scope> argument = ArgumentCaptor.forClass(Scope.class);

        when(scopeRepository.findById(scopeId)).thenReturn(Maybe.just(toUpdate));
        when(scopeRepository.update(argument.capture())).thenReturn(Single.just(new Scope()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = scopeService.update(DOMAIN,scopeId, updateScope).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(scopeRepository, times(1)).update(any(Scope.class));
        assertNotNull(argument.getValue());
        assertEquals("name",argument.getValue().getName());
        assertNull(argument.getValue().getDescription());
        assertFalse(argument.getValue().isDiscovery());
    }

    @Test
    public void shouldUpdate_nonSystemScope_discoveryNotReplaced() {
        UpdateScope updateScope = new UpdateScope();
        updateScope.setName("name");

        final String scopeId = "toUpdateId";

        Scope toUpdate = new Scope();
        toUpdate.setId(scopeId);
        toUpdate.setSystem(false);
        toUpdate.setDiscovery(true);
        toUpdate.setName("oldName");
        toUpdate.setDescription("oldDescription");

        ArgumentCaptor<Scope> argument = ArgumentCaptor.forClass(Scope.class);

        when(scopeRepository.findById(scopeId)).thenReturn(Maybe.just(toUpdate));
        when(scopeRepository.update(argument.capture())).thenReturn(Single.just(new Scope()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = scopeService.update(DOMAIN,scopeId, updateScope).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(scopeRepository, times(1)).update(any(Scope.class));
        assertNotNull(argument.getValue());
        assertEquals("name",argument.getValue().getName());
        assertNull(argument.getValue().getDescription());
        assertTrue(argument.getValue().isDiscovery());
    }

    @Test
    public void shouldUpdate_nonSystemScope_discoveryReplaced() {
        UpdateScope updateScope = new UpdateScope();
        updateScope.setDiscovery(true);
        updateScope.setName("name");

        final String scopeId = "toUpdateId";

        Scope toUpdate = new Scope();
        toUpdate.setId(scopeId);
        toUpdate.setSystem(false);
        toUpdate.setDiscovery(false);
        toUpdate.setName("oldName");
        toUpdate.setDescription("oldDescription");

        ArgumentCaptor<Scope> argument = ArgumentCaptor.forClass(Scope.class);

        when(scopeRepository.findById(scopeId)).thenReturn(Maybe.just(toUpdate));
        when(scopeRepository.update(argument.capture())).thenReturn(Single.just(new Scope()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = scopeService.update(DOMAIN,scopeId, updateScope).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(scopeRepository, times(1)).update(any(Scope.class));
        assertNotNull(argument.getValue());
        assertEquals("name",argument.getValue().getName());
        assertNull(argument.getValue().getDescription());
        assertTrue(argument.getValue().isDiscovery());
    }

    @Test
    public void shouldNotUpdate() {
        when(scopeRepository.findById("toUpdateId")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = scopeService.update(DOMAIN,"toUpdateId", new UpdateScope()).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldNotUpdate_malformedIconUri() {
        UpdateScope updateScope = new UpdateScope();
        updateScope.setIconUri("malformedIconUri");

        when(scopeRepository.findById("toUpdateId")).thenReturn(Maybe.just(new Scope()));

        TestObserver testObserver = new TestObserver();
        scopeService.update(DOMAIN, "toUpdateId",updateScope).subscribe(testObserver);

        testObserver.assertError(MalformedIconUriException.class);
        testObserver.assertNotComplete();

        verify(scopeRepository, times(1)).findById("toUpdateId");
        verify(scopeRepository, never()).update(any(Scope.class));
    }

    @Test
    public void shouldUpdateSystemScope() {
        UpdateSystemScope updateScope = new UpdateSystemScope();
        updateScope.setDiscovery(true);
        updateScope.setName("name");

        final String scopeId = "toUpdateId";

        Scope toUpdate = new Scope();
        toUpdate.setId(scopeId);
        toUpdate.setSystem(true);
        toUpdate.setDiscovery(false);
        toUpdate.setName("oldName");
        toUpdate.setDescription("oldDescription");

        ArgumentCaptor<Scope> argument = ArgumentCaptor.forClass(Scope.class);

        when(scopeRepository.findById(scopeId)).thenReturn(Maybe.just(toUpdate));
        when(scopeRepository.update(argument.capture())).thenReturn(Single.just(new Scope()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = scopeService.update(DOMAIN,scopeId, updateScope).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(scopeRepository, times(1)).update(any(Scope.class));
        assertNotNull(argument.getValue());
        assertEquals("name",argument.getValue().getName());
        assertNull(argument.getValue().getDescription());
        assertTrue(argument.getValue().isDiscovery());
    }

    @Test
    public void shouldNotUpdateSystemScope() {
        Scope toUpdate = new Scope();
        toUpdate.setId("toUpdateId");

        when(scopeRepository.findById("toUpdateId")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = scopeService.update(DOMAIN,"toUpdateId", new UpdateSystemScope()).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_notExistingScope() {
        when(scopeRepository.findById("my-scope")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        scopeService.delete("my-scope", false).subscribe(testObserver);

        testObserver.assertError(ScopeNotFoundException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_technicalException() {
        when(scopeRepository.findById("my-scope")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        scopeService.delete("my-scope", false).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete2_technicalException() {
        when(scopeRepository.findById("my-scope")).thenReturn(Maybe.just(new Scope()));

        TestObserver testObserver = new TestObserver();
        scopeService.delete("my-scope", false).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete3_technicalException() {
        when(scopeRepository.findById("my-scope")).thenReturn(Maybe.just(new Scope()));

        TestObserver testObserver = new TestObserver();
        scopeService.delete("my-scope", false).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_light() {
        Scope scope = mock(Scope.class);
        when(scope.getDomain()).thenReturn(DOMAIN);
        when(roleService.findByDomain(DOMAIN)).thenReturn(Single.just(Collections.emptySet()));
        when(applicationService.findByDomain(DOMAIN)).thenReturn(Single.just(Collections.emptySet()));
        when(scopeRepository.findById("my-scope")).thenReturn(Maybe.just(scope));
        when(scopeRepository.delete("my-scope")).thenReturn(Completable.complete());
        when(scopeApprovalRepository.deleteByDomainAndScopeKey(scope.getDomain(), scope.getKey())).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = scopeService.delete("my-scope", false).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(roleService, times(1)).findByDomain(DOMAIN);
        verify(applicationService, times(1)).findByDomain(DOMAIN);
        verify(scopeRepository, times(1)).delete("my-scope");
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldDelete_full() {
        Scope scope = mock(Scope.class);
        when(scope.getDomain()).thenReturn(DOMAIN);
        when(scope.getKey()).thenReturn("my-scope");

        Role role = mock(Role.class);
        when(role.getId()).thenReturn("role-1");
        when(role.getOauthScopes()).thenReturn(new LinkedList<>(Arrays.asList("my-scope")));

        Application application = mock(Application.class);

        ApplicationSettings applicationSettings = mock(ApplicationSettings.class);
        ApplicationOAuthSettings applicationOAuthSettings = mock(ApplicationOAuthSettings.class);
        when(applicationOAuthSettings.getScopes()).thenReturn(new LinkedList<>(Arrays.asList("my-scope")));
        when(applicationSettings.getOauth()).thenReturn(applicationOAuthSettings);
        when(application.getSettings()).thenReturn(applicationSettings);

        when(roleService.findByDomain(DOMAIN)).thenReturn(Single.just(Collections.singleton(role)));
        when(applicationService.findByDomain(DOMAIN)).thenReturn(Single.just(Collections.singleton(application)));
        when(roleService.update(anyString(), anyString(), any(UpdateRole.class))).thenReturn(Single.just(new Role()));
        when(applicationService.update(any())).thenReturn(Single.just(new Application()));
        when(scopeRepository.findById("my-scope")).thenReturn(Maybe.just(scope));
        when(scopeRepository.delete("my-scope")).thenReturn(Completable.complete());
        when(scopeApprovalRepository.deleteByDomainAndScopeKey(scope.getDomain(), scope.getKey())).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = scopeService.delete("my-scope", false).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(roleService, times(1)).findByDomain(DOMAIN);
        verify(applicationService, times(1)).findByDomain(DOMAIN);
        verify(roleService, times(1)).update(anyString(), anyString(), any(UpdateRole.class));
        verify(applicationService, times(1)).update(any());
        verify(scopeRepository, times(1)).delete("my-scope");
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldNotDeleteSystemScope() throws TechnicalException {
        Scope scope = new Scope();
        scope.setKey("scope-key");
        scope.setSystem(true);
        when(scopeRepository.findById("scope-id")).thenReturn(Maybe.just(scope));

        TestObserver testObserver = scopeService.delete("scope-id", false).test();
        testObserver.assertError(SystemScopeDeleteException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void validateScope_nullList() {
        TestObserver<Boolean> testObserver = scopeService.validateScope(DOMAIN,null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(isValid -> isValid);
    }

    @Test
    public void validateScope_unknownScope() {
        when(scopeRepository.findByDomain(DOMAIN)).thenReturn(Single.just(Collections.singleton(new Scope("valid"))));
        TestObserver<Boolean> testObserver = scopeService.validateScope(DOMAIN,Arrays.asList("unknown")).test();
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    public void validateScope_validScope() {
        when(scopeRepository.findByDomain(DOMAIN)).thenReturn(Single.just(Collections.singleton(new Scope("valid"))));
        TestObserver<Boolean> testObserver = scopeService.validateScope(DOMAIN,Arrays.asList("valid")).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(isValid -> isValid);
    }
}
