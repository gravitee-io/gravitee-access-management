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
package io.gravitee.am.service.idp;

import com.nimbusds.jose.util.JSONObjectUtils;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.exception.InvalidParameterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author GraviteeSource Team
 */
class SystemClusterIdpPolicyTest {

    private static final String IDP_ID = "idp-1";

    private static final String PLATFORM_DATABASE = "gravitee-am";

    private final MockEnvironment environment = new MockEnvironment();
    private final SystemClusterIdpPolicy policy = new SystemClusterIdpPolicy();

    @BeforeEach
    void setUp() {
        environment.setProperty("repositories.management.mongodb.dbname", PLATFORM_DATABASE);
        enableManagedCloud(environment);
        ReflectionTestUtils.setField(policy, "environment", environment);
    }

    private static void enableManagedCloud(MockEnvironment environment) {
        environment.setProperty("cloud.enabled", "true");
        environment.setProperty("installation.type", "managed");
    }

    @Test
    void should_pin_database_and_collection_in_managed_cloud() throws ParseException {
        var idp = mongoIdp(configuration(true, "custom-db", "my-users", null));

        policy.applyOnCreate(idp);

        var configuration = JSONObjectUtils.parse(idp.getConfiguration());
        assertEquals(PLATFORM_DATABASE, configuration.get("database"));
        assertEquals("idp_" + IDP_ID, configuration.get("usersCollection"));
        assertTrue(idp.isSystemClusterRestricted());
    }

    @Test
    void should_leave_the_configuration_alone_when_not_in_managed_cloud() {
        var standalone = new SystemClusterIdpPolicy();
        ReflectionTestUtils.setField(standalone, "environment", new MockEnvironment());
        var original = configuration(true, "custom-db", "my-users", null);
        var idp = mongoIdp(original);

        standalone.applyOnCreate(idp);

        assertEquals(original, idp.getConfiguration());
        assertFalse(idp.isSystemClusterRestricted());
    }

    @Test
    void should_leave_the_configuration_alone_when_cloud_is_enabled_but_installation_is_standalone() {
        var selfHosted = new SystemClusterIdpPolicy();
        var selfHostedEnv = new MockEnvironment();
        selfHostedEnv.setProperty("cloud.enabled", "true");
        selfHostedEnv.setProperty("installation.type", "standalone");
        ReflectionTestUtils.setField(selfHosted, "environment", selfHostedEnv);
        var original = configuration(true, "custom-db", "my-users", null);
        var idp = mongoIdp(original);

        selfHosted.applyOnCreate(idp);

        assertEquals(original, idp.getConfiguration());
        assertFalse(idp.isSystemClusterRestricted());
    }

    @Test
    void should_leave_the_database_alone_when_the_system_cluster_has_no_dbname() throws ParseException {
        var bare = new SystemClusterIdpPolicy();
        var bareEnv = new MockEnvironment();
        enableManagedCloud(bareEnv);
        ReflectionTestUtils.setField(bare, "environment", bareEnv);
        var idp = mongoIdp(configuration(true, "custom-db", "my-users", null));

        bare.applyOnCreate(idp);

        var configuration = JSONObjectUtils.parse(idp.getConfiguration());
        assertEquals("custom-db", configuration.get("database"));
        assertEquals("idp_" + IDP_ID, configuration.get("usersCollection"));
        assertTrue(idp.isSystemClusterRestricted());
    }

    @Test
    void should_take_the_database_from_the_system_cluster_uri() throws ParseException {
        var uriEnv = managedCloudEnvironment();
        uriEnv.setProperty("repositories.management.mongodb.uri", "mongodb://mongodb:27017/graviteeam");
        uriEnv.setProperty("repositories.management.mongodb.dbname", PLATFORM_DATABASE);
        var uriPolicy = policyWith(uriEnv);
        var idp = mongoIdp(configuration(true, "custom-db", "my-users", null));

        uriPolicy.applyOnCreate(idp);

        assertEquals("graviteeam", JSONObjectUtils.parse(idp.getConfiguration()).get("database"));
    }

    @Test
    void should_fall_back_to_dbname_when_the_uri_carries_no_database() throws ParseException {
        var uriEnv = managedCloudEnvironment();
        uriEnv.setProperty("repositories.management.mongodb.uri", "mongodb://mongodb:27017");
        uriEnv.setProperty("repositories.management.mongodb.dbname", PLATFORM_DATABASE);
        var uriPolicy = policyWith(uriEnv);
        var idp = mongoIdp(configuration(true, "custom-db", "my-users", null));

        uriPolicy.applyOnCreate(idp);

        assertEquals(PLATFORM_DATABASE, JSONObjectUtils.parse(idp.getConfiguration()).get("database"));
    }

    @Test
    void should_read_the_uri_of_the_configured_system_cluster_scope() throws ParseException {
        var gatewayEnv = managedCloudEnvironment();
        gatewayEnv.setProperty("repositories.system-cluster", "gateway");
        gatewayEnv.setProperty("repositories.management.mongodb.uri", "mongodb://mongodb:27017/management-db");
        gatewayEnv.setProperty("repositories.gateway.mongodb.uri", "mongodb://mongodb:27017/gateway-db");
        var gatewayPolicy = policyWith(gatewayEnv);
        var idp = mongoIdp(configuration(true, "custom-db", "my-users", null));

        gatewayPolicy.applyOnCreate(idp);

        assertEquals("gateway-db", JSONObjectUtils.parse(idp.getConfiguration()).get("database"));
    }

    @Test
    void should_leave_the_database_alone_when_pinning_is_turned_off() throws ParseException {
        environment.setProperty(SystemClusterIdpPolicy.PIN_DATABASE, "false");
        var idp = mongoIdp(configuration(true, "custom-db", "my-users", null));

        policy.applyOnCreate(idp);

        var configuration = JSONObjectUtils.parse(idp.getConfiguration());
        assertEquals("custom-db", configuration.get("database"));
        assertEquals("idp_" + IDP_ID, configuration.get("usersCollection"));
        assertTrue(idp.isSystemClusterRestricted());
    }

    @Test
    void should_leave_the_collection_alone_when_the_prefix_is_turned_off() throws ParseException {
        environment.setProperty(SystemClusterIdpPolicy.PREFIX_USERS_COLLECTION, "false");
        var idp = mongoIdp(configuration(true, "custom-db", "my-users", null));

        policy.applyOnCreate(idp);

        var configuration = JSONObjectUtils.parse(idp.getConfiguration());
        assertEquals(PLATFORM_DATABASE, configuration.get("database"));
        assertEquals("my-users", configuration.get("usersCollection"));
        assertTrue(idp.isSystemClusterRestricted());
    }

    @Test
    void should_leave_the_configuration_alone_when_both_settings_are_turned_off() {
        environment.setProperty(SystemClusterIdpPolicy.PIN_DATABASE, "false");
        environment.setProperty(SystemClusterIdpPolicy.PREFIX_USERS_COLLECTION, "false");
        var original = configuration(true, "custom-db", "my-users", null);
        var idp = mongoIdp(original);

        policy.applyOnCreate(idp);

        assertEquals(original, idp.getConfiguration());
        assertFalse(idp.isSystemClusterRestricted());
    }

    @Test
    void should_pin_outside_managed_cloud_when_the_settings_are_turned_on() throws ParseException {
        var selfHostedEnv = new MockEnvironment();
        selfHostedEnv.setProperty("repositories.management.mongodb.dbname", PLATFORM_DATABASE);
        selfHostedEnv.setProperty(SystemClusterIdpPolicy.PIN_DATABASE, "true");
        selfHostedEnv.setProperty(SystemClusterIdpPolicy.PREFIX_USERS_COLLECTION, "true");
        var selfHosted = policyWith(selfHostedEnv);
        var idp = mongoIdp(configuration(true, "custom-db", "my-users", null));

        selfHosted.applyOnCreate(idp);

        var configuration = JSONObjectUtils.parse(idp.getConfiguration());
        assertEquals(PLATFORM_DATABASE, configuration.get("database"));
        assertEquals("idp_" + IDP_ID, configuration.get("usersCollection"));
        assertTrue(idp.isSystemClusterRestricted());
    }

    @Test
    void should_skip_a_system_identity_provider() {
        var idp = mongoIdp(configuration(true, "custom-db", "my-users", null));
        idp.setSystem(true);

        policy.applyOnCreate(idp);

        assertFalse(idp.isSystemClusterRestricted());
    }

    @Test
    void should_skip_a_non_mongo_identity_provider() {
        var idp = mongoIdp(configuration(true, "custom-db", "my-users", null));
        idp.setType("inline-am-idp");

        policy.applyOnCreate(idp);

        assertFalse(idp.isSystemClusterRestricted());
    }

    @Test
    void should_skip_when_the_identity_provider_names_a_datasource() {
        var idp = mongoIdp(configuration(true, "custom-db", "my-users", "ds-1"));

        policy.applyOnCreate(idp);

        assertFalse(idp.isSystemClusterRestricted());
    }

    @Test
    void should_skip_when_the_identity_provider_does_not_reuse_the_system_cluster() {
        var idp = mongoIdp(configuration(false, "custom-db", "my-users", null));

        policy.applyOnCreate(idp);

        assertFalse(idp.isSystemClusterRestricted());
    }

    @Test
    void should_leave_an_existing_system_cluster_provider_alone_on_update() {
        var stored = mongoIdp(configuration(true, "custom-db", "my-users", null));
        var toUpdate = mongoIdp(configuration(true, "other-db", "other-users", null));

        policy.applyOnUpdate(stored, toUpdate);

        assertFalse(toUpdate.isSystemClusterRestricted());
        assertEquals(configuration(true, "other-db", "other-users", null), toUpdate.getConfiguration());
    }

    @Test
    void should_pin_when_an_update_turns_the_system_cluster_on() throws ParseException {
        var stored = mongoIdp(configuration(false, "custom-db", "my-users", null));
        var toUpdate = mongoIdp(configuration(true, "custom-db", "my-users", null));

        policy.applyOnUpdate(stored, toUpdate);

        var configuration = JSONObjectUtils.parse(toUpdate.getConfiguration());
        assertEquals(PLATFORM_DATABASE, configuration.get("database"));
        assertEquals("idp_" + IDP_ID, configuration.get("usersCollection"));
        assertTrue(toUpdate.isSystemClusterRestricted());
    }

    @Test
    void should_not_pin_an_update_that_turns_the_system_cluster_on_outside_managed_cloud() {
        var standalone = new SystemClusterIdpPolicy();
        ReflectionTestUtils.setField(standalone, "environment", new MockEnvironment());
        var stored = mongoIdp(configuration(false, "custom-db", "my-users", null));
        var original = configuration(true, "custom-db", "my-users", null);
        var toUpdate = mongoIdp(original);

        standalone.applyOnUpdate(stored, toUpdate);

        assertEquals(original, toUpdate.getConfiguration());
        assertFalse(toUpdate.isSystemClusterRestricted());
    }

    @Test
    void should_not_pin_an_update_that_turns_the_system_cluster_on_for_a_datasource_provider() {
        var stored = mongoIdp(configuration(false, "custom-db", "my-users", "ds-1"));
        var original = configuration(true, "custom-db", "my-users", "ds-1");
        var toUpdate = mongoIdp(original);

        policy.applyOnUpdate(stored, toUpdate);

        assertEquals(original, toUpdate.getConfiguration());
        assertFalse(toUpdate.isSystemClusterRestricted());
    }

    @Test
    void should_allow_an_unchanged_configuration_on_a_flagged_identity_provider() {
        var stored = restrictedIdp(configuration(true, null, "idp_" + IDP_ID, null));

        policy.applyOnUpdate(stored, mongoIdp(configuration(true, null, "idp_" + IDP_ID, null)));
    }

    @Test
    void should_reject_a_changed_collection_on_a_flagged_identity_provider() {
        var stored = restrictedIdp(configuration(true, null, "idp_" + IDP_ID, null));
        var toUpdate = mongoIdp(configuration(true, null, "my-users", null));

        assertThrows(InvalidParameterException.class, () -> policy.applyOnUpdate(stored, toUpdate));
    }

    @Test
    void should_reject_a_changed_database_on_a_flagged_identity_provider() {
        var stored = restrictedIdp(configuration(true, null, "idp_" + IDP_ID, null));
        var toUpdate = mongoIdp(configuration(true, "custom-db", "idp_" + IDP_ID, null));

        assertThrows(InvalidParameterException.class, () -> policy.applyOnUpdate(stored, toUpdate));
    }

    @Test
    void should_reject_a_changed_use_system_cluster_on_a_flagged_identity_provider() {
        var stored = restrictedIdp(configuration(true, null, "idp_" + IDP_ID, null));
        var toUpdate = mongoIdp(configuration(false, null, "idp_" + IDP_ID, null));

        assertThrows(InvalidParameterException.class, () -> policy.applyOnUpdate(stored, toUpdate));
    }

    @Test
    void should_reject_a_datasource_added_to_a_flagged_identity_provider() {
        var stored = restrictedIdp(configuration(true, null, "idp_" + IDP_ID, null));
        var toUpdate = mongoIdp(configuration(true, null, "idp_" + IDP_ID, "ds-1"));

        assertThrows(InvalidParameterException.class, () -> policy.applyOnUpdate(stored, toUpdate));
    }

    @Test
    void should_reject_an_unreadable_configuration_on_a_flagged_identity_provider() {
        var stored = restrictedIdp(configuration(true, null, "idp_" + IDP_ID, null));
        var toUpdate = mongoIdp("not json");

        assertThrows(InvalidParameterException.class, () -> policy.applyOnUpdate(stored, toUpdate));
    }

    private static MockEnvironment managedCloudEnvironment() {
        var environment = new MockEnvironment();
        enableManagedCloud(environment);
        return environment;
    }

    private static SystemClusterIdpPolicy policyWith(MockEnvironment environment) {
        var policy = new SystemClusterIdpPolicy();
        ReflectionTestUtils.setField(policy, "environment", environment);
        return policy;
    }

    private IdentityProvider mongoIdp(String configuration) {
        var idp = new IdentityProvider();
        idp.setId(IDP_ID);
        idp.setType(SystemClusterIdpPolicy.MONGO_IDP_TYPE);
        idp.setConfiguration(configuration);
        return idp;
    }

    private IdentityProvider restrictedIdp(String configuration) {
        var idp = mongoIdp(configuration);
        idp.setSystemClusterRestricted(true);
        return idp;
    }

    private String configuration(boolean useSystemCluster, String database, String usersCollection, String datasourceId) {
        var configuration = new java.util.LinkedHashMap<String, Object>();
        configuration.put("uri", "mongodb://localhost:27017");
        configuration.put("useSystemCluster", useSystemCluster);
        if (database != null) {
            configuration.put("database", database);
        }
        configuration.put("usersCollection", usersCollection);
        if (datasourceId != null) {
            configuration.put("datasourceId", datasourceId);
        }
        return JSONObjectUtils.toJSONString(configuration);
    }
}
