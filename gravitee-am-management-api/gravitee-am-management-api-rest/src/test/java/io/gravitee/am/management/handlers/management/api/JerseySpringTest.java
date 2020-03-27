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
package io.gravitee.am.management.handlers.management.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.manager.certificate.CertificateManager;
import io.gravitee.am.management.handlers.management.api.manager.group.GroupManager;
import io.gravitee.am.management.handlers.management.api.mapper.ObjectMapperResolver;
import io.gravitee.am.management.service.*;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.*;
import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.reactivex.Single;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import javax.annotation.Priority;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
public abstract class JerseySpringTest {

    protected static final String USER_NAME = "UnitTests";

    protected ObjectMapper objectMapper = new ObjectMapperResolver().getContext(ObjectMapper.class);

    @Autowired
    protected DomainService domainService;

    @Autowired
    protected io.gravitee.am.management.service.UserService userService;

    @Autowired
    protected ScopeService scopeService;

    @Autowired
    protected RoleService roleService;

    @Autowired
    protected IdentityProviderService identityProviderService;

    @Autowired
    protected ExtensionGrantService extensionGrantService;

    @Autowired
    protected CertificateService certificateService;

    @Autowired
    protected ClientService clientService;

    @Autowired
    protected CertificatePluginService certificatePluginService;

    @Autowired
    protected TokenService tokenService;

    @Autowired
    protected ExtensionGrantPluginService extensionGrantPluginService;

    @Autowired
    protected IdentityProviderPluginService identityProviderPluginService;

    @Autowired
    protected CertificateManager certificateManager;

    @Autowired
    protected IdentityProviderManager identityProviderManager;

    @Autowired
    protected EmailTemplateService emailTemplateService;

    @Autowired
    protected EmailManager emailManager;

    @Autowired
    protected FormService formService;

    @Autowired
    protected PasswordValidator passwordValidator;

    @Autowired
    protected ScopeApprovalService scopeApprovalService;

    @Autowired
    protected AuditService auditService;

    @Autowired
    protected AuditReporterManager AuditReporterManager;

    @Autowired
    protected ReporterService reporterService;

    @Autowired
    protected TagService tagService;

    @Autowired
    protected GroupService groupService;

    @Autowired
    protected ApplicationService applicationService;

    @Autowired
    protected GroupManager groupManager;

    @Autowired
    protected FactorService factorService;

    @Autowired
    protected PermissionService permissionService;

    @Autowired
    protected OrganizationService organizationService;

    @Autowired
    protected MembershipService membershipService;

    @Before
    public void init() {
       when(permissionService.hasPermission(any(User.class), any(PermissionAcls.class))).thenReturn(Single.just(true));
    }

    @Configuration
    @ComponentScan("io.gravitee.am.management.handlers.management.api.resources.enhancer")
    static class ContextConfiguration {

        @Bean
        public OrganizationService organizationService() {
            return mock(OrganizationService.class);
        }

        @Bean
        public EnvironmentService environmentService() {
            return mock(EnvironmentService.class);
        }

        @Bean
        public DomainService domainService() {
            return mock(DomainService.class);
        }

        @Bean
        public io.gravitee.am.management.service.UserService userService() {
            return mock(io.gravitee.am.management.service.UserService.class);
        }

        @Bean
        public ScopeService scopeService() {
            return mock(ScopeService.class);
        }

        @Bean
        public RoleService roleService() {
            return mock(RoleService.class);
        }

        @Bean
        public IdentityProviderService identityProviderService() {
            return mock(IdentityProviderService.class);
        }

        @Bean
        public ExtensionGrantService extensionGrantService() {
            return mock(ExtensionGrantService.class);
        }

        @Bean
        public CertificateService certificateService() {
            return mock(CertificateService.class);
        }

        @Bean
        public ClientService clientService() {
            return mock(ClientService.class);
        }

        @Bean
        public CertificatePluginService certificatePluginService() {
            return mock(CertificatePluginService.class);
        }

        @Bean
        public TokenService tokenService() {
            return mock(TokenService.class);
        }

        @Bean
        public ExtensionGrantPluginService extensionGrantPluginService() {
            return mock(ExtensionGrantPluginService.class);
        }

        @Bean
        public IdentityProviderPluginService identityProviderPluginService() {
            return mock(IdentityProviderPluginService.class);
        }

        @Bean
        public CertificateManager certificateManager() {
            return mock(CertificateManager.class);
        }

        @Bean
        public CertificatePluginManager certificatePluginManager() {
            return mock(CertificatePluginManager.class);
        }

        @Bean
        public IdentityProviderManager identityProviderManager() {
            return mock(IdentityProviderManager.class);
        }

        @Bean
        public EmailTemplateService emailTemplateService() {
            return mock(EmailTemplateService.class);
        }

        @Bean
        public EmailManager emailManager() {
            return mock(EmailManager.class);
        }

        @Bean
        public FormService formService() {
            return mock(FormService.class);
        }

        @Bean
        public PasswordValidator passwordValidator() {
            return mock(PasswordValidator.class);
        }

        @Bean
        public ScopeApprovalService scopeApprovalService() {
            return mock(ScopeApprovalService.class);
        }

        @Bean
        public AuditService auditService() {
            return mock(AuditService.class);
        }

        @Bean
        public AuditReporterManager auditReporterManager() {
            return mock(AuditReporterManager.class);
        }

        @Bean
        public ReporterService reporterService() {
            return mock(ReporterService.class);
        }

        @Bean
        public TagService tagService() {
            return mock(TagService.class);
        }

        @Bean
        public GroupService groupService() {
            return mock(GroupService.class);
        }

        @Bean
        public ApplicationService applicationService() {
            return mock(ApplicationService.class);
        }

        @Bean
        public GroupManager groupManager() {
            return mock(GroupManager.class);
        }

        @Bean
        public FactorService factorService() {
            return mock(FactorService.class);
        }

        @Bean
        public PermissionService permissionService(){
            return mock(PermissionService.class);
        }

        @Bean
        public MembershipService membershipService(){
            return mock(MembershipService.class);
        }
    }

    private JerseyTest _jerseyTest;

    public final WebTarget target(final String path) {

        if("domains".equals(path)) {
            return _jerseyTest.target("organizations").path("DEFAULT").path("environments").path("DEFAULT").path(path);
        }

        return _jerseyTest.target(path);
    }

    @Before
    public void setup() throws Exception {
        _jerseyTest.setUp();
    }

    @After
    public void tearDown() throws Exception {
        _jerseyTest.tearDown();
    }

    @Autowired
    public void setApplicationContext(final ApplicationContext context) {
        _jerseyTest = new JerseyTest() {
            @Override
            protected Application configure() {
                ResourceConfig application = new ManagementApplication();
                application.register(AuthenticationFilter.class);
                application.property("contextConfig", context);

                return application;
            }
        };
    }

    @Priority(50)
    public static class AuthenticationFilter implements ContainerRequestFilter {
        @Override
        public void filter(final ContainerRequestContext requestContext) throws IOException {
            requestContext.setSecurityContext(new SecurityContext() {
                @Override
                public Principal getUserPrincipal() {
                    User endUser = new DefaultUser(USER_NAME);
                    return new UsernamePasswordAuthenticationToken(endUser, null);
                }

                @Override
                public boolean isUserInRole(String string) {
                    return true;
                }

                @Override
                public boolean isSecure() {
                    return true;
                }

                @Override
                public String getAuthenticationScheme() {
                    return "BASIC";
                }
            });
        }
    }

    /**
     * Allows to read response entity using object mapper instead of jersey's own implementation.
     * It is especially useful to ensure objects are deserialize from json using same feature (ex: lower cased enum, ...).
     *
     * @param response the jaxrs response.
     * @param clazz the type of entity
     * @param <T> the expected type of entity.
     *
     * @return the deserialized entity.
     */
    protected <T> T readEntity(Response response, Class<T> clazz) {

        try {
            if(clazz == String.class) {
                return (T) response.readEntity(String.class);
            }
            return objectMapper.readValue(response.readEntity(String.class), clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
