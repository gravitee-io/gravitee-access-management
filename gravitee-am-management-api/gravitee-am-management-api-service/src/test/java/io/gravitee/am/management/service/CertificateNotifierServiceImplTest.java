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
package io.gravitee.am.management.service;

import io.gravitee.am.management.service.impl.CertificateNotifierServiceImpl;
import io.gravitee.am.management.service.impl.DomainOwnersProvider;
import io.gravitee.am.management.service.impl.notifications.notifiers.NotifierSettings;
import io.gravitee.am.management.service.impl.notifications.definition.CertificateNotifierSubject;
import io.gravitee.am.management.service.impl.notifications.definition.NotificationDefinitionFactory;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.node.api.notifier.NotificationDefinition;
import io.gravitee.node.api.notifier.NotifierService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CertificateNotifierServiceImplTest {

    public static final String DOMAIN_ID = UUID.randomUUID().toString();
    public static final String ORGANIZATION_ID = "org-" + DOMAIN_ID;
    public static final String ENV_ID = "env-" + DOMAIN_ID;

    @InjectMocks
    private CertificateNotifierServiceImpl cut;

    @Mock
    private NotifierService notifierService;

    @Mock
    private DomainService domainService;

    @Mock
    private DomainOwnersProvider domainOwnersProvider;

    @Spy
    private NotifierSettings notifierSettings = new NotifierSettings(true, Template.CERTIFICATE_EXPIRATION, "* * * * *", List.of(20,15,10), "subject");

    @Spy
    private List<NotificationDefinitionFactory<CertificateNotifierSubject>> notificationDefinitionFactories = List.of(mockDef());


    private static NotificationDefinitionFactory<CertificateNotifierSubject> mockDef(){
        NotificationDefinition notificationDefinition = new NotificationDefinition();
        notificationDefinition.setResourceId("certificateId");
        return object -> Maybe.just(notificationDefinition);
    }

    private Certificate certificate;
    private Domain domain;

    @Before
    public void prepareTest() throws Exception {

        domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setReferenceId(ENV_ID);
        domain.setReferenceType(ReferenceType.ENVIRONMENT);

        certificate = new Certificate();
        certificate.setDomain(domain.getId());
        certificate.setExpiresAt(Date.from(Instant.now().plus(60, ChronoUnit.DAYS)));

        when(domainService.findById(certificate.getDomain())).thenReturn(Maybe.just(domain));

    }

    @Test
    public void shouldNotNotifyUser_NoExpiryCertificate() throws Exception {
        certificate.setExpiresAt(null);

        cut.registerCertificateExpiration(certificate);
        verify(notifierService,never()).register(any(), any(), any());
    }

    @Test
    public void shouldNotifyUser_EmailOnly() throws Exception {
        final User user = new User();
        user.setEmail("user@acme.fr");
        when(domainOwnersProvider.retrieveDomainOwners(eq(domain))).thenReturn(Flowable.just(user));

        cut.registerCertificateExpiration(certificate);

        Thread.sleep(1000); // wait subscription execution

        verify(notifierService).register(any(), any(), any());
    }

    @Test
    public void shouldNotifyUser_EmailAndUI() throws Exception {

        final User user = new User();
        user.setEmail("user@acme.fr");
        when(domainOwnersProvider.retrieveDomainOwners(eq(domain))).thenReturn(Flowable.just(user));

        cut.registerCertificateExpiration(certificate);

        Thread.sleep(1000); // wait subscription execution

        verify(notifierService).register(argThat(def -> def.getResourceId().equals("certificateId")), any(), any());
    }

    @Test
    public void shouldNotifyUserFromGroup() throws Exception {
        final List<User> tenUsers = IntStream.range(0, 10).mapToObj(x -> {
            final User user = new User();
            user.setId("" + x);
            user.setEmail(x+"@acme.fr");
            return user;
        }).toList();
        final User singleUser = new User();
        singleUser.setId("single");
        singleUser.setEmail("single@acme.fr");

        when(domainOwnersProvider.retrieveDomainOwners(eq(domain))).thenReturn(Flowable.fromIterable(tenUsers).mergeWith(Flowable.just(singleUser)));

        cut.registerCertificateExpiration(certificate);

        Thread.sleep(1000); // wait subscription execution

        verify(notifierService, times(11)).register(any(), any(), any());
    }

    @Test
    public void shouldNotNotifyIfGroupIsEmpty() throws Exception {
        when(domainOwnersProvider.retrieveDomainOwners(eq(domain))).thenReturn(Flowable.empty());

        cut.registerCertificateExpiration(certificate);

        Thread.sleep(1000); // wait subscription execution

        verify(notifierService, never()).register(any(), any(), any());
    }

    @Test
    public void shouldNotifyLog() throws InterruptedException {
        final User user = new User();
        user.setEmail("user@acme.fr");
        when(domainOwnersProvider.retrieveDomainOwners(eq(domain))).thenReturn(Flowable.just(user));



        cut.registerCertificateExpiration(certificate);
        Thread.sleep(1000); // wait subscription execution

        verify(notifierService, times(1)).register(any(), any(), any());
    }
}
