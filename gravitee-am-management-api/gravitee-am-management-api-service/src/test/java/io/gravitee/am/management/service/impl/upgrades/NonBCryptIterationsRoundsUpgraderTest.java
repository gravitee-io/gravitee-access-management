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
package io.gravitee.am.management.service.impl.upgrades;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static io.reactivex.rxjava3.core.Flowable.just;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NonBCryptIterationsRoundsUpgraderTest {

    @Mock
    private IdentityProviderService idpService;

    @Mock
    private SystemTaskRepository systemTaskRepository;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private NonBCryptIterationsRoundsUpgrader upgrader;

    @Before
    public void setUp() {
        SystemTask task = new SystemTask();
        task.setOperationId("op1");
        Mockito.when(systemTaskRepository.updateIf(any(), any()))
                .thenReturn(Single.just(task));
    }

    @Test
    public void shouldIgnoreIfTaskCompleted() {
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.SUCCESS.name());
        when(systemTaskRepository.findById(any())).thenReturn(Maybe.just(task));

        upgrader.upgrade();

        verify(systemTaskRepository, times(1)).findById(any());
        verify(idpService, never()).findAll();
    }

    @Test
    public void shouldUpdateAllNonBCryptIDPPasswordEncoderOptions() throws Exception {
        // given
        ArgumentCaptor<UpdateIdentityProvider> captor = ArgumentCaptor.captor();
        Mockito.when(idpService.findAll())
                .thenReturn(just(
                        idp("1", "{\"uri\":\"mongodb://localhost:27017\",\"host\":\"localhost\",\"port\":27017,\"enableCredentials\":false,\"passwordCredentials\":null,\"database\":\"m2\",\"usersCollection\":\"users\",\"findUserByUsernameQuery\":\"{username: ?}\",\"findUserByEmailQuery\":\"{email: ?}\",\"usernameField\":\"username\",\"passwordField\":\"password\",\"passwordEncoder\":\"SHA-256+MD5\",\"passwordEncoderOptions\":{\"rounds\":106},\"useDedicatedSalt\":false,\"passwordSaltLength\":32,\"passwordSaltFormat\":\"DIGEST\",\"userProvider\":true,\"usernameCaseSensitive\":false}"),
                        idp("2", "{\"uri\":\"mongodb://localhost:27017/?connectTimeoutMS=1000&socketTimeoutMS=1000\",\"host\":\"localhost\",\"port\":\"27017\",\"enableCredentials\":false,\"database\":\"gravitee-am\",\"usersCollection\":\"idp_users_58b8632e-a482-4c11-b863-2ea482cc1169\",\"findUserByUsernameQuery\":\"{username: ?}\",\"findUserByEmailQuery\":\"{email: ?}\",\"usernameField\":\"username\",\"passwordField\":\"password\",\"passwordEncoder\":\"BCrypt\",\"passwordEncoderOptions\":{\"rounds\":10}}"),
                        idp("3", "{\"uri\":\"mongodb://localhost:27017/?connectTimeoutMS=1000&socketTimeoutMS=1000\",\"host\":\"localhost\",\"port\":\"27017\",\"enableCredentials\":false,\"database\":\"gravitee-am\",\"usersCollection\":\"idp_users_58b8632e-a482-4c11-b863-2ea482cc1169\",\"findUserByUsernameQuery\":\"{username: ?}\",\"findUserByEmailQuery\":\"{email: ?}\",\"usernameField\":\"username\",\"passwordField\":\"password\",\"passwordEncoder\":\"BCrypt\",\"passwordEncoderOptions\":{\"rounds\":100}}"),
                        idp("4", "{\"uri\":\"mongodb://localhost:27017\",\"host\":\"localhost\",\"port\":27017,\"enableCredentials\":false,\"passwordCredentials\":null,\"database\":\"m2\",\"usersCollection\":\"users\",\"findUserByUsernameQuery\":\"{username: ?}\",\"findUserByEmailQuery\":\"{email: ?}\",\"usernameField\":\"username\",\"passwordField\":\"password\",\"passwordEncoder\":\"SHA-256+MD5\",\"passwordEncoderOptions\":{\"rounds\":106},\"useDedicatedSalt\":false,\"passwordSaltLength\":32,\"passwordSaltFormat\":\"DIGEST\",\"userProvider\":true,\"usernameCaseSensitive\":false}")
                        ));


        Mockito.when(idpService.update(any(), any(), any(), captor.capture(), any(), anyBoolean()))
                .thenReturn(Single.fromSupplier(IdentityProvider::new));

        // when
        TestObserver<Boolean> observer = new TestObserver();
        upgrader.processUpgrade("op1", new SystemTask(), "op11")
                .subscribe(observer);

        observer.assertNoErrors();

        Assertions.assertEquals(2, captor.getAllValues().size());

        UpdateIdentityProvider idp1 = captor.getAllValues().get(0);
        Assertions.assertEquals("1", idp1.getName());

        UpdateIdentityProvider idp4 = captor.getAllValues().get(1);
        Assertions.assertEquals("4", idp4.getName());

        JsonNode jsonNode1 = new ObjectMapper().readTree(idp1.getConfiguration());
        Assertions.assertFalse(jsonNode1.has("passwordEncoderOptions"));
        Assertions.assertTrue(jsonNode1.has("passwordEncoder"));
        Assertions.assertTrue(jsonNode1.has("uri"));

        JsonNode jsonNode2 = new ObjectMapper().readTree(idp4.getConfiguration());
        Assertions.assertFalse(jsonNode2.has("passwordEncoderOptions"));
        Assertions.assertTrue(jsonNode2.has("passwordEncoder"));
        Assertions.assertTrue(jsonNode2.has("uri"));
    }

    @Test
    public void shouldNotUpdateBCryptIDPPasswordEncoderOptions() {
        // given
        Mockito.when(idpService.findAll())
                .thenReturn(just(
                        idp("2", "{\"uri\":\"mongodb://localhost:27017/?connectTimeoutMS=1000&socketTimeoutMS=1000\",\"host\":\"localhost\",\"port\":\"27017\",\"enableCredentials\":false,\"database\":\"gravitee-am\",\"usersCollection\":\"idp_users_58b8632e-a482-4c11-b863-2ea482cc1169\",\"findUserByUsernameQuery\":\"{username: ?}\",\"findUserByEmailQuery\":\"{email: ?}\",\"usernameField\":\"username\",\"passwordField\":\"password\",\"passwordEncoder\":\"BCrypt\",\"passwordEncoderOptions\":{\"rounds\":10}}")
                ));

        // when
        TestObserver<Boolean> observer = new TestObserver();
        upgrader.processUpgrade("op1", new SystemTask(), "op11")
                .subscribe(observer);

        observer.assertNoErrors();

        Mockito.verify(idpService, times(0)).update(any(), any(), any(), any(), any(), anyBoolean());
    }

    private IdentityProvider idp(String id, String configuration) {
        IdentityProvider idp = new IdentityProvider();
        idp.setId(id);
        idp.setName(id);
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId("domain-id");
        idp.setConfiguration(configuration);
        return idp;
    }
}
