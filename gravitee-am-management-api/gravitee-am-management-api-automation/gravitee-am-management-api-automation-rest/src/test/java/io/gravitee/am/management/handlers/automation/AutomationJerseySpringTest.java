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
package io.gravitee.am.management.handlers.automation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.mapper.ObjectMapperResolver;
import io.gravitee.am.management.service.DefaultIdentityProviderService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.PermissionService;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Organization;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.ReporterService;
import io.reactivex.rxjava3.core.Single;
import jakarta.annotation.Priority;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Jersey + Spring test harness for the Automation API, mirroring the management REST
 * API's {@code JerseySpringTest} pattern but scoped to the (much smaller) set of beans
 * the Automation resources require. Boots the real {@link AutomationApiApplication} so
 * routing, providers and (de)serialization are exercised end-to-end against mocked
 * services.
 *
 * @author GraviteeSource Team
 */
@SpringJUnitConfig(classes = AutomationJerseySpringTest.ContextConfiguration.class)
public abstract class AutomationJerseySpringTest {

    protected static final String USER_NAME = "UnitTests";
    protected static final String ORG_ID = "DEFAULT";
    protected static final String ENV_ID = "DEFAULT";

    protected ObjectMapper objectMapper = new ObjectMapperResolver()
            .getContext(ObjectMapper.class)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Autowired
    protected PermissionService permissionService;

    @Autowired
    protected DomainService domainService;

    @Autowired
    protected CertificateService certificateService;

    @Autowired
    protected IdentityProviderService identityProviderService;

    @Autowired
    protected DefaultIdentityProviderService defaultIdentityProviderService;

    @Autowired
    protected ReporterService reporterService;

    @BeforeEach
    public void init() {
        // The service mocks are Spring singletons shared across the cached context, so clear any
        // invocations recorded by a previous test before each one runs — this keeps verify(...) checks
        // (e.g. "delete was never called") scoped to the test at hand.
        clearInvocations(permissionService, domainService, certificateService, identityProviderService,
                defaultIdentityProviderService, reporterService);
        when(permissionService.hasPermission(any(User.class), any(PermissionAcls.class)))
                .thenReturn(Single.just(true));
    }

    /**
     * Re-stubs the permission service to deny every check, so tests can assert that authorization is
     * enforced (403) ahead of any resource lookup — i.e. without leaking existence as a 404.
     */
    protected void denyPermission() {
        when(permissionService.hasPermission(any(User.class), any(PermissionAcls.class)))
                .thenReturn(Single.just(false));
    }

    @Configuration
    static class ContextConfiguration {

        @Bean
        public PermissionService permissionService() {
            return mock(PermissionService.class);
        }

        @Bean
        public DomainService domainService() {
            return mock(DomainService.class);
        }

        @Bean
        public CertificateService certificateService() {
            return mock(CertificateService.class);
        }

        @Bean
        public IdentityProviderService identityProviderService() {
            return mock(IdentityProviderService.class);
        }

        @Bean
        public DefaultIdentityProviderService defaultIdentityProviderService() {
            return mock(DefaultIdentityProviderService.class);
        }

        @Bean
        public ReporterService reporterService() {
            return mock(ReporterService.class);
        }
    }

    private JerseyTest jerseyTest;

    /** Root of every Automation API path: {@code /organizations/{orgId}}. */
    protected final WebTarget orgTarget() {
        return jerseyTest.target("organizations").path(ORG_ID);
    }

    /** {@code /organizations/{orgId}/environments/{envId}/domains}. */
    protected final WebTarget domainsTarget() {
        return orgTarget().path("environments").path(ENV_ID).path("domains");
    }

    /** {@code /organizations/{orgId}/environments/{envId}/domains/{domainKey}/identity-providers}. */
    protected final WebTarget identityProvidersTarget(String domainKey) {
        return domainsTarget().path(domainKey).path("identity-providers");
    }

    /** {@code /organizations/{orgId}/environments/{envId}/domains/{domainKey}/certificates}. */
    protected final WebTarget certificatesTarget(String domainKey) {
        return domainsTarget().path(domainKey).path("certificates");
    }

    /** {@code /organizations/{orgId}/environments/{envId}/domains/{domainKey}/reporters}. */
    protected final WebTarget reportersTarget(String domainKey) {
        return domainsTarget().path(domainKey).path("reporters");
    }

    @BeforeEach
    public void setup() throws Exception {
        jerseyTest.setUp();
    }

    @AfterEach
    public void tearDown() throws Exception {
        jerseyTest.tearDown();
    }

    @Autowired
    public void setApplicationContext(final ApplicationContext context) {
        jerseyTest = new JerseyTest() {
            @Override
            protected Application configure() {
                ResourceConfig application = new AutomationApiApplication();
                application.register(AuthenticationFilter.class);
                application.property("contextConfig", context);
                return application;
            }
        };
    }

    /**
     * Stands in for {@code AutomationBearerTokenFilter}: sets a {@link SecurityContext}
     * whose principal is the {@link UsernamePasswordAuthenticationToken} that
     * {@code AbstractAutomationResource#getAuthenticatedUser()} expects.
     */
    @Priority(50)
    public static class AuthenticationFilter implements ContainerRequestFilter {
        @Override
        public void filter(final ContainerRequestContext requestContext) {
            requestContext.setSecurityContext(new SecurityContext() {
                @Override
                public Principal getUserPrincipal() {
                    DefaultUser endUser = new DefaultUser(USER_NAME);
                    endUser.setAdditionalInformation(Map.of(Claims.ORGANIZATION, Organization.DEFAULT));
                    return new UsernamePasswordAuthenticationToken(endUser, null);
                }

                @Override
                public boolean isUserInRole(String role) {
                    return true;
                }

                @Override
                public boolean isSecure() {
                    return true;
                }

                @Override
                public String getAuthenticationScheme() {
                    return "BEARER";
                }
            });
        }
    }

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

    protected <T> T readEntity(Response response, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(response.readEntity(String.class), typeReference);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> List<T> readListEntity(Response response, Class<T> entityClazz) {
        try {
            return objectMapper.readValue(response.readEntity(String.class),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, entityClazz));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> Response put(WebTarget webTarget, T value) {
        try {
            return webTarget.request().put(Entity.entity(
                    objectMapper.writeValueAsString(value), MediaType.APPLICATION_JSON_TYPE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
