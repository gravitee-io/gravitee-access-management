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
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.common.email.Email;
import io.gravitee.am.management.service.impl.DomainNotifierServiceImpl;
import io.gravitee.am.model.*;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.*;
import io.gravitee.am.service.spring.email.EmailConfiguration;
import io.gravitee.node.api.notifier.NotifierService;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
    private GroupService groupService;

    @Mock
    private OrganizationUserService userService;

    @Mock
    private EmailConfiguration emailConfiguration;

    @Mock
    private EmailService emailService;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private CertificateProvider provider;

    private Certificate certificate;
    private Domain domain;
    private Environment env;

    @Before
    public void prepareTest() {
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

        when(domainService.findById(certificate.getDomain())).thenReturn(Maybe.just(domain));

        when(environmentService.findById(domain.getReferenceId())).thenReturn(Single.just(env));

        final Role role = new Role();
        role.setId("role#1");
        when(roleService.findSystemRole(SystemRole.DOMAIN_PRIMARY_OWNER, ReferenceType.DOMAIN)).thenReturn(Maybe.just(role));
        final Role role2 = new Role();
        role2.setId("role#2");
        when(roleService.findDefaultRole(ORGANIZATION_ID, DefaultRole.DOMAIN_OWNER, ReferenceType.DOMAIN)).thenReturn(Maybe.just(role2));

    }

    @Test
    public void shouldNotifyUser() throws Exception {
        final Membership member = new Membership();
        member.setMemberType(MemberType.USER);
        member.setMemberId("userid");
        when(membershipService.findByCriteria(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), any())).thenReturn(Flowable.just(member), Flowable.empty());

        final User user = new User();
        user.setEmail("user@acme.fr");
        when(userService.findById(ReferenceType.ORGANIZATION, env.getOrganizationId(), member.getMemberId())).thenReturn(Single.just(user));

        when(emailConfiguration.isEnabled()).thenReturn(true);

        when(emailService.getFinalEmail(any(), any(), any(), any(), any())).thenReturn(new Email());

        cut.registerCertificateExpiration(provider, certificate);

        Thread.sleep(1000); // wait subscription execution

        verify(notifierService).register(any(), any());
        verify(groupService, never()).findMembers(any(), any(), any(), anyInt(), anyInt());
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
        }).collect(Collectors.toList());
        final User singleUser = new User();
        singleUser.setId("single");
        singleUser.setEmail("single@acme.fr");

        when(groupService.findMembers(any(), any(), any(), anyInt(), anyInt())).thenReturn(
                Single.just(new Page<>(tenUsers, 0, 11)),
                Single.just(new Page<>(Arrays.asList(singleUser), 1, 11)));

        when(emailConfiguration.isEnabled()).thenReturn(true);

        when(emailService.getFinalEmail(any(), any(), any(), any(), any())).thenReturn(new Email());

        cut.registerCertificateExpiration(provider, certificate);

        Thread.sleep(1000); // wait subscription execution

        verify(notifierService, times(11)).register(any(), any());
        verify(userService, never()).findById(any(), any(), any());

    }
}
