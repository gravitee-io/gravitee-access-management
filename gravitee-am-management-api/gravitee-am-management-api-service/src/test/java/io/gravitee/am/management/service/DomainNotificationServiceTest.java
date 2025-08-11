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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.email.Email;
import io.gravitee.am.management.service.impl.DomainNotifierServiceImpl;
import io.gravitee.am.management.service.impl.notifications.EmailNotifierConfiguration;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationGroupService;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.RoleService;
import io.gravitee.node.api.notifier.NotifierService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_EMAIL_NOTIFIER;
import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_UI_NOTIFIER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
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
public class DomainNotificationServiceTest {

    public static final String DOMAIN_ID = UUID.randomUUID().toString();
    public static final String ORGANIZATION_ID = "org-" + DOMAIN_ID;
    public static final String ENV_ID = "env-" + DOMAIN_ID;

    @InjectMocks
    private DomainNotifierServiceImpl cut;

    @Mock
    private NotifierService notifierService;

    @Mock
    private MembershipService membershipService;

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private DomainService domainService;

    @Mock
    private RoleService roleService;

    @Mock
    private OrganizationGroupService organizationGroupService;

    @Mock
    private OrganizationUserService userService;

    @Mock
    private EmailNotifierConfiguration emailConfiguration;

    @Mock
    private EmailService emailService;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private org.springframework.core.env.Environment propertiesEnv;

    private Certificate certificate;
    private Domain domain;
    private Environment env;

    @Before
    public void prepareTest() throws Exception {
        when(propertiesEnv.getProperty("services.certificate.expiryThresholds", String.class, DomainNotifierServiceImpl.DEFAULT_CERTIFICATE_EXPIRY_THRESHOLDS))
                .thenReturn(DomainNotifierServiceImpl.DEFAULT_CERTIFICATE_EXPIRY_THRESHOLDS);
        ReflectionTestUtils.setField(cut, "emailNotifierEnabled", true);
        env = new Environment();
        env.setId(ENV_ID);
        env.setOrganizationId(ORGANIZATION_ID);

        domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setReferenceId(ENV_ID);
        domain.setReferenceType(ReferenceType.ENVIRONMENT);

        certificate = new Certificate();
        certificate.setDomain(domain.getId());
        certificate.setExpiresAt(Date.from(Instant.now().plus(60, ChronoUnit.DAYS)));

        when(domainService.findById(certificate.getDomain())).thenReturn(Maybe.just(domain));

        when(environmentService.findById(domain.getReferenceId())).thenReturn(Single.just(env));

        final Role role = new Role();
        role.setId("role#1");
        when(roleService.findSystemRole(SystemRole.DOMAIN_PRIMARY_OWNER, ReferenceType.DOMAIN)).thenReturn(Maybe.just(role));
        final Role role2 = new Role();
        role2.setId("role#2");
        when(roleService.findDefaultRole(ORGANIZATION_ID, DefaultRole.DOMAIN_OWNER, ReferenceType.DOMAIN)).thenReturn(Maybe.just(role2));

        cut.afterPropertiesSet();
    }

    @After
    public void cleanUp() {
        ReflectionTestUtils.setField(cut, "uiNotifierEnabled", false);
    }

    @Test
    public void shouldNotNotifyUser_NoExpiryCertificate() throws Exception {
        certificate.setExpiresAt(null);

        cut.registerCertificateExpiration(certificate);
        verify(notifierService,never()).register(any(), any(), any());
    }

    @Test
    public void shouldNotifyUser_EmailOnly() throws Exception {
        final Membership member = new Membership();
        member.setMemberType(MemberType.USER);
        member.setMemberId("userid");
        when(membershipService.findByCriteria(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), any())).thenReturn(Flowable.just(member), Flowable.empty());

        final User user = new User();
        user.setEmail("user@acme.fr");
        when(userService.findById(ReferenceType.ORGANIZATION, env.getOrganizationId(), member.getMemberId())).thenReturn(Single.just(user));

        when(emailService.getFinalEmail(any(), any(), any(), any(), any())).thenReturn(Maybe.just(new Email()));

        cut.registerCertificateExpiration(certificate);

        Thread.sleep(1000); // wait subscription execution

        verify(notifierService).register(any(), any(), any());
        verify(organizationGroupService, never()).findMembers(any(), any(), anyInt(), anyInt());
    }

    @Test
    public void shouldNotifyUser_EmailAndUI() throws Exception {
        ReflectionTestUtils.setField(cut, "uiNotifierEnabled", true);

        final Membership member = new Membership();
        member.setMemberType(MemberType.USER);
        member.setMemberId("userid");
        when(membershipService.findByCriteria(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), any())).thenReturn(Flowable.just(member), Flowable.empty());

        final User user = new User();
        user.setEmail("user@acme.fr");
        when(userService.findById(ReferenceType.ORGANIZATION, env.getOrganizationId(), member.getMemberId())).thenReturn(Single.just(user));

        when(emailService.getFinalEmail(any(), any(), any(), any(), any())).thenReturn(Maybe.just(new Email()));

        cut.registerCertificateExpiration(certificate);

        Thread.sleep(1000); // wait subscription execution

        verify(notifierService).register(argThat(def -> def.getType().equals(TYPE_UI_NOTIFIER)), any(), any());
        verify(notifierService).register(argThat(def -> def.getType().equals(TYPE_EMAIL_NOTIFIER)), any(), any());
        verify(organizationGroupService, never()).findMembers(any(), any(), anyInt(), anyInt());
    }

    @Test
    public void shouldNotifyUserFromGroup() throws Exception {
        final Membership member = new Membership();
        member.setMemberType(MemberType.GROUP);
        member.setMemberId("groupId");
        when(membershipService.findByCriteria(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), any())).thenReturn(Flowable.just(member), Flowable.empty());

        final List<User> tenUsers = IntStream.range(0, 10).mapToObj(x -> {
            final User user = new User();
            user.setId("" + x);
            user.setEmail(x+"@acme.fr");
            return user;
        }).toList();
        final User singleUser = new User();
        singleUser.setId("single");
        singleUser.setEmail("single@acme.fr");

        when(organizationGroupService.findMembers(any(), any(), anyInt(), anyInt())).thenReturn(
                Single.just(new Page<>(tenUsers, 0, 11)),
                Single.just(new Page<>(Arrays.asList(singleUser), 1, 11)));

        when(emailService.getFinalEmail(any(), any(), any(), any(), any())).thenReturn(Maybe.just(new Email()));

        cut.registerCertificateExpiration(certificate);

        Thread.sleep(1000); // wait subscription execution

        verify(notifierService, times(11)).register(any(), any(), any());
        verify(userService, never()).findById(any(), any(), any());
    }

    @Test
    public void shouldNotNotifyIfGroupIsEmpty() throws Exception {
        final Membership member = new Membership();
        member.setMemberType(MemberType.GROUP);
        member.setMemberId("groupId");
        when(membershipService.findByCriteria(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), any())).thenReturn(Flowable.just(member), Flowable.empty());
        when(organizationGroupService.findMembers(any(), any(), anyInt(), anyInt())).thenReturn(Single.just(new Page<>(null, 0, 0)));

        cut.registerCertificateExpiration(certificate);

        Thread.sleep(1000); // wait subscription execution

        verify(notifierService, never()).register(any(), any(), any());
        verify(userService, never()).findById(any(), any(), any());
    }

    @Test
    public void shouldNotifyLog() throws InterruptedException {
        final Membership member = new Membership();
        member.setMemberType(MemberType.USER);
        member.setMemberId("userid");
        when(membershipService.findByCriteria(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), any())).thenReturn(Flowable.just(member), Flowable.empty());

        final User user = new User();
        user.setEmail("user@acme.fr");
        when(userService.findById(ReferenceType.ORGANIZATION, env.getOrganizationId(), member.getMemberId())).thenReturn(Single.just(user));

        ReflectionTestUtils.setField(cut, "isLogNotifierEnabled", true);
        ReflectionTestUtils.setField(cut, "emailNotifierEnabled", false);


        cut.registerCertificateExpiration(certificate);
        Thread.sleep(1000); // wait subscription execution

        verify(notifierService, times(1)).register(any(), any(), any());
    }
}
