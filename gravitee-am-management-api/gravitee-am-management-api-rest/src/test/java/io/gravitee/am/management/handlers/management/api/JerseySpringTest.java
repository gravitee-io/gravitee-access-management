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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.authentication.view.TemplateResolver;
import io.gravitee.am.management.handlers.management.api.mapper.ObjectMapperResolver;
import io.gravitee.am.management.handlers.management.api.preview.PreviewService;
import io.gravitee.am.management.service.*;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.BotDetectionService;
import io.gravitee.am.service.CertificatePluginService;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.DeviceIdentifierService;
import io.gravitee.am.service.DeviceService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.EmailTemplateService;
import io.gravitee.am.service.EntrypointService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.ExtensionGrantService;
import io.gravitee.am.service.FactorService;
import io.gravitee.am.service.FlowService;
import io.gravitee.am.service.FormService;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.TagService;
import io.gravitee.am.service.ThemeService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Single;
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
import org.thymeleaf.TemplateEngine;

import javax.annotation.Priority;
import javax.inject.Named;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.security.Principal;
import java.util.List;

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
    @Named("managementOrganizationUserService")
    protected OrganizationUserService organizationUserService;

    @Autowired
    protected DomainService domainService;

    @Autowired
    protected io.gravitee.am.management.service.UserService userService;

    @Autowired
    protected UserActivityService userActivityService;

    @Autowired
    protected ScopeService scopeService;

    @Autowired
    protected RoleService roleService;

    @Autowired
    protected IdentityProviderServiceProxy identityProviderService;

    @Autowired
    protected ExtensionGrantService extensionGrantService;

    @Autowired
    protected CertificateServiceProxy certificateService;

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
    protected UserValidator userValidator;

    @Autowired
    protected ScopeApprovalService scopeApprovalService;

    @Autowired
    protected AuditService auditService;

    @Autowired
    protected AuditReporterManager AuditReporterManager;

    @Autowired
    protected ReporterServiceProxy reporterService;

    @Autowired
    protected TagService tagService;

    @Autowired
    protected GroupService groupService;

    @Autowired
    protected ApplicationService applicationService;

    @Autowired
    protected FactorService factorService;

    @Autowired
    protected FactorPluginService factorPluginService;

    @Autowired
    protected PermissionService permissionService;

    @Autowired
    protected OrganizationService organizationService;

    @Autowired
    protected MembershipService membershipService;

    @Autowired
    protected EntrypointService entrypointService;

    @Autowired
    protected CredentialService credentialService;

    @Autowired
    protected FlowService flowService;

    @Autowired
    protected ResourcePluginService resourcePluginService;

    @Autowired
    protected BotDetectionPluginService botDetectionPluginService;

    @Autowired
    protected BotDetectionServiceProxy botDetectionService;

    @Autowired
    protected DeviceIdentifierPluginService deviceIdentifierPluginService;

    @Autowired
    protected DeviceIdentifierService deviceIdentifierService;

    @Autowired
    protected DeviceService deviceService;

    @Autowired
    protected AuthenticationDeviceNotifierPluginService authDeviceNotifierPluginService;

    @Autowired
    protected ThemeService themeService;

    @Autowired
    protected PreviewService previewService;
    @Autowired
    protected I18nDictionaryService i18nDictionaryService;

    @Autowired
    protected PolicyPluginService policyPluginService;

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

        @Bean("managementOrganizationUserService")
        public OrganizationUserService organizationUserService() {
            return mock(OrganizationUserService.class);
        }

        @Bean
        public io.gravitee.am.management.service.UserService userService() {
            return mock(io.gravitee.am.management.service.UserService.class);
        }

        @Bean
        public UserActivityService userActivityService() {
            return mock(UserActivityService.class);
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
        public IdentityProviderServiceProxy identityProviderServiceProxy() {
            return mock(IdentityProviderServiceProxy.class);
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
        public CertificateServiceProxy certificateServiceProxy() {
            return mock(CertificateServiceProxy.class);
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
        public AmPluginManager<CertificateManager> certificatePluginManager() {
            return mock(AmPluginManager.class);
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
        public PasswordService passwordService() {
            return mock(PasswordService.class);
        }

        @Bean
        public UserValidator userValidator() {
            return mock(UserValidator.class);
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
        public ReporterServiceProxy reporterServiceProxy() {
            return mock(ReporterServiceProxy.class);
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
        public FactorService factorService() {
            return mock(FactorService.class);
        }

        @Bean
        public FactorPluginService factorPluginService() {
            return mock(FactorPluginService.class);
        }

        @Bean
        public PermissionService permissionService() {
            return mock(PermissionService.class);
        }

        @Bean
        public MembershipService membershipService() {
            return mock(MembershipService.class);
        }

        @Bean
        public EntrypointService entrypointService() {
            return mock(EntrypointService.class);
        }

        @Bean
        public CredentialService credentialService() {
            return mock(CredentialService.class);
        }

        @Bean
        public FlowService flowService() {
            return mock(FlowService.class);
        }

        @Bean
        public ResourcePluginService resourcePluginService() {
            return mock(ResourcePluginService.class);
        }

        @Bean
        public BotDetectionPluginService botDetectionPluginService() {
            return mock(BotDetectionPluginService.class);
        }

        @Bean
        public BotDetectionService botDetectionService() {
            return mock(BotDetectionService.class);
        }

        @Bean
        public BotDetectionServiceProxy botDetectionServiceProxy() {
            return mock(BotDetectionServiceProxy.class);
        }

        @Bean
        public DeviceIdentifierPluginService deviceIdentifierPluginService() {
            return mock(DeviceIdentifierPluginService.class);
        }

        @Bean
        public DeviceIdentifierService deviceIdentifierService() {
            return mock(DeviceIdentifierService.class);
        }

        @Bean
        public DeviceService deviceService() {
            return mock(DeviceService.class);
        }

        @Bean
        public AuthenticationDeviceNotifierPluginService authDeviceNotifierPluginService() {
            return mock(AuthenticationDeviceNotifierPluginService.class);
        }

        @Bean
        public ThemeService themeService() {
            return mock(ThemeService.class);
        }

        @Bean
        public PreviewService previewService() {
            return mock(PreviewService.class);
        }

        @Bean
        public PolicyPluginService policyPluginService() {
            return mock(PolicyPluginService.class);
        }

        @Bean
        public I18nDictionaryService i18nDictionaryService() {
            return mock(I18nDictionaryService.class);
        }

        @Bean
        public TemplateEngine templateEngine() {
            return mock(TemplateEngine.class);
        }

        @Bean
        public TemplateResolver templateResolver() {
            return mock(TemplateResolver.class);
        }

    }

    private JerseyTest _jerseyTest;

    public final WebTarget target(final String path) {

        if ("domains".equals(path)) {
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
     * It is especially useful to ensure objects are deserialized from json using same feature (ex: lower cased enum, ...).
     *
     * @param response the jaxrs response.
     * @param clazz the type of entity
     * @param <T> the expected type of entity.
     *
     * @return the deserialized entity.
     */
    protected <T> T readEntity(Response response, Class<T> clazz) {

        try {
            if (clazz == String.class) {
                return (T) response.readEntity(String.class);
            }

            return objectMapper.readValue(response.readEntity(String.class), clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> T readEntity(Response response, TypeReference<T> clazz) {

        try {
            return objectMapper.readValue(response.readEntity(String.class), clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> List<T> readListEntity(Response response, Class<T> entityClazz) {

        try {
            return objectMapper.readValue(response.readEntity(String.class), objectMapper.getTypeFactory().constructCollectionType(List.class, entityClazz));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Allows to put entity using object mapper instead of jersey's own implementation.
     * It is especially useful to ensure objects are serialized to json using same feature (ex: lower cased enum, ...).
     *
     * @param webTarget the jersey web target.
     * @param value the entity to put.
     * @param <T> the type of entity.
     *
     * @return the resulted {@link Response}.
     */
    protected <T> Response put(WebTarget webTarget, T value) {

        try {
            return webTarget.request().put(Entity.entity(objectMapper.writeValueAsString(value), MediaType.APPLICATION_JSON_TYPE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> Response post(WebTarget webTarget, T value) {

        try {
            return webTarget.request().post(Entity.entity(objectMapper.writeValueAsString(value), MediaType.APPLICATION_JSON_TYPE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
