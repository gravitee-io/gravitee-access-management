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
package io.gravitee.am.management.service.impl.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.service.PolicyPluginService;
import io.gravitee.am.management.service.impl.plugins.PolicyPluginServiceImpl;
import io.gravitee.am.management.service.impl.policy.dummy.DummyManifest;
import io.gravitee.am.management.service.impl.policy.dummy.DummyPolicy;
import io.gravitee.am.plugins.policy.core.PolicyPluginManager;
import io.gravitee.am.service.exception.PluginNotDeployedException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.plugin.core.api.PluginManifest;
import io.gravitee.plugin.policy.PolicyPlugin;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class PolicyPluginServiceTest {

    private static final String FINAL_SCHEMA = "{\"field\":\"value\",\"properties\":{}}";
    private static final String SCHEMA_WITH_UNWANTED_FIELDS = "{\"field\" : \"value\", " +
                "\"properties\" : {" +
                    "\"scope\" : \"something\"," +
                    "\"onResponseScript\" : \"something\"," +
                    "\"onRequestContentScript\" : \"something\"," +
                    "\"onResponseContentScript\" : \"something\"" +
                "}" +
            "}";
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private PolicyPluginManager policyPluginManager;
    private PolicyPluginService policyPluginService;

    @BeforeEach
    public void setUp() {
        policyPluginService = new PolicyPluginServiceImpl(
                policyPluginManager, objectMapper
        );
    }

    @ParameterizedTest
    @MethodSource("params_that_must_find_all")
    public void must_find_all(Collection<PolicyPlugin> policies, List<String> expand, int expectedSize) {
        when(policyPluginManager.getAll(eq(true))).thenReturn(policies);

        var observer = policyPluginService.findAll(expand).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertNoErrors()
                .assertComplete()
                .assertValue(value -> value.size() == expectedSize);
    }

    private static Stream<Arguments> params_that_must_find_all() {
        return Stream.of(
                createNPolicies(0, null),
                createNPolicies(0, List.of()),
                createNPolicies(0, List.of("schema")),
                createNPolicies(0, List.of("icon")),
                createNPolicies(0, List.of("schema", "icon")),
                createNPolicies(1, null),
                createNPolicies(1, List.of()),
                createNPolicies(1, List.of("schema")),
                createNPolicies(1, List.of("icon")),
                createNPolicies(1, List.of("schema", "icon")),
                createNPolicies(5, null),
                createNPolicies(5, List.of()),
                createNPolicies(5, List.of("schema")),
                createNPolicies(5, List.of("icon")),
                createNPolicies(5, List.of("schema", "icon"))
        );
    }

    private static Arguments createNPolicies(int size, List<String> expand) {
        if (size == 0) {
            return Arguments.of(List.of(), null, size);
        }
        var policies = range(0, size).mapToObj(PolicyPluginServiceTest::getPolicyPlugin).collect(toList());
        return Arguments.of(policies, expand, size);
    }

    @ParameterizedTest
    @MethodSource("params_that_must_not_find_by_id")
    public void must_not_find_by_id(String id, PolicyPlugin policy) {
        when(policyPluginManager.get(id)).thenReturn(policy);

        var observer = policyPluginService.findById(id).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
    }

    private static Stream<Arguments> params_that_must_not_find_by_id() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("1", null),
                Arguments.of("1", null)
        );
    }

    @Test
    public void must_not_find_by_id_with_error() {
        when(policyPluginManager.get("someId")).thenThrow(new RuntimeException("An error has occurred"));

        var observer = policyPluginService.findById("someId").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(TechnicalManagementException.class);
    }

    @Test
    public void must_find_by_id() {
        when(policyPluginManager.get(any())).thenReturn(new DummyPolicy(new DummyManifest("125")));
        var observer = policyPluginService.findById("125").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertValue(policyPlugin -> policyPlugin.getId().equals("125"));
    }

    @Test
    public void must_not_get_schema() throws IOException {
        when(policyPluginManager.getSchema(any())).thenThrow(new IOException("Could not get schema"));

        var observer = policyPluginService.getSchema("125").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(TechnicalManagementException.class);
    }

    @ParameterizedTest
    @MethodSource("params_that_must_get_schema")
    public void must_get_schema(String schema, String expectedValue) throws IOException {
        when(policyPluginManager.getSchema(any())).thenReturn(schema);

        var observer = policyPluginService.getSchema("125").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertValue(expectedValue::equals);
    }

    public static Stream<Arguments> params_that_must_get_schema() {
        return Stream.of(
                Arguments.of(FINAL_SCHEMA, FINAL_SCHEMA),
                Arguments.of(SCHEMA_WITH_UNWANTED_FIELDS, FINAL_SCHEMA)
        );
    }


    @Test
    public void must_not_get_icon() throws IOException {
        when(policyPluginManager.getIcon(any())).thenThrow(new IOException("Could not get icon"));

        var observer = policyPluginService.getIcon("125").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(TechnicalManagementException.class);
    }

    @Test
    public void must_get_icon() throws IOException {
        String icon = "icon.svg";
        when(policyPluginManager.getIcon(any())).thenReturn(icon);

        var observer = policyPluginService.getIcon("125").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertValue(icon::equals);
    }

    @Test
    public void must_not_get_documentation() throws IOException {
        when(policyPluginManager.getDocumentation(any())).thenThrow(new IOException("Could not get documentation"));

        var observer = policyPluginService.getDocumentation("125").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(TechnicalManagementException.class);
    }

    @Test
    public void must_get_documentation() throws IOException {
        String documentation = "Some documentation";
        when(policyPluginManager.getDocumentation(any())).thenReturn(documentation);

        var observer = policyPluginService.getDocumentation("125").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertValue(documentation::equals);
    }

    private static PolicyPlugin getPolicyPlugin(int id) {
        var plugin = Mockito.mock(PolicyPlugin.class);
        var manifest = Mockito.mock(PluginManifest.class);
        when(manifest.id()).thenReturn(String.valueOf(id));
        when(plugin.manifest()).thenReturn(manifest);
        return plugin;
    }

    @Test
    public void must_accept_deployed_plugins() throws IOException {
        String pluginId = "pluginId";

        DummyPolicy policy = new DummyPolicy(new DummyManifest(pluginId), true);
        when(policyPluginManager.get(pluginId)).thenReturn(policy);

        var observer = policyPluginService.checkPluginDeployment(pluginId).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
    }

    @Test
    public void must_reject_not_deployed_plugins() throws IOException {
        String pluginId = "pluginId";

        DummyPolicy policy = new DummyPolicy(new DummyManifest(pluginId), false);
        when(policyPluginManager.get(pluginId)).thenReturn(policy);

        var observer = policyPluginService.checkPluginDeployment(pluginId).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(PluginNotDeployedException.class);
    }

    @Test
    public void must_reject_unknown_plugin() throws IOException {
        String pluginId = "pluginId";

        when(policyPluginManager.get(pluginId)).thenReturn(null);

        var observer = policyPluginService.checkPluginDeployment(pluginId).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(PluginNotDeployedException.class);
    }
}
