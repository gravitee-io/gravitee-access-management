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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.am.repository.management.test.IncompatibleDataTestUtils;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.test.util.ReflectionTestUtils;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RoleRepositoryTest extends AbstractManagementTest {
    public static final String DOMAIN_ID = "domain#1";
    private static final String TEST_ROLE_NAME = "testName";
    private static final String FUTURE_REFERENCE_TYPE = "FUTURE_REFERENCE_TYPE";
    private static final String FUTURE_ASSIGNABLE_TYPE = "FUTURE_ASSIGNABLE_TYPE";

    @Autowired
    private RoleRepository roleRepository;

    @Test
    public void testFindByDomain() {
        // create role
        Role role = new Role();
        role.setName(TEST_ROLE_NAME);
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setReferenceId("testDomain");
        roleRepository.create(role).blockingGet();

        // fetch roles
        TestObserver<List<Role>> testObserver = roleRepository.findAll(ReferenceType.DOMAIN, "testDomain").toList().test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(roles -> roles.size() == 1);
    }

    @Test
    public void testFindByNamesAndAssignable() {
        // create role
        Role role = new Role();
        role.setName(TEST_ROLE_NAME);
        role.setReferenceType(ReferenceType.PLATFORM);
        role.setReferenceId(Platform.DEFAULT);
        role.setAssignableType(ReferenceType.ORGANIZATION);
        roleRepository.create(role).blockingGet();

        Role role2 = new Role();
        final String NAME_1 = TEST_ROLE_NAME;
        final String NAME_2 = "testName2";
        role2.setName(NAME_2);
        role2.setReferenceType(ReferenceType.PLATFORM);
        role2.setReferenceId(Platform.DEFAULT);
        role2.setAssignableType(ReferenceType.ORGANIZATION);
        roleRepository.create(role2).blockingGet();

        Role role3 = new Role();
        final String NAME_3 = "testName3";
        role3.setName(NAME_3);
        role3.setReferenceType(ReferenceType.PLATFORM);
        role3.setReferenceId(Platform.DEFAULT);
        role3.setAssignableType(ReferenceType.ORGANIZATION);
        roleRepository.create(role3).blockingGet();

        Role role4 = new Role();
        final String NAME_4 = "testName4";
        role4.setName(NAME_4);
        role4.setReferenceType(ReferenceType.PLATFORM);
        role4.setReferenceId(Platform.DEFAULT);
        role4.setAssignableType(ReferenceType.ENVIRONMENT);
        roleRepository.create(role4).blockingGet();

        // fetch roles 1 & 2
        TestObserver<List<Role>> testObserver = roleRepository.findByNamesAndAssignableType(ReferenceType.PLATFORM, Platform.DEFAULT, Arrays.asList(NAME_1, NAME_2), ReferenceType.ORGANIZATION).toList().test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(roles -> roles.size() == 2 && roles.stream().map(Role::getName).collect(Collectors.toList()).containsAll(Arrays.asList(NAME_1, NAME_2)));
    }

    @Test
    public void testFindById() {
        // create role
        Role role = buildRole();
        Role roleCreated = roleRepository.create(role).blockingGet();

        // fetch role
        TestObserver<Role> testObserver = roleRepository.findById(roleCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(r -> r.getId().equals(roleCreated.getId()));
        assertEqualsTo(role, testObserver);
    }

    private Role buildRole() {
        Role role = new Role();
        String random = UUID.randomUUID().toString();
        role.setSystem(true);
        role.setDefaultRole(true);
        role.setName("name"+random);
        role.setDescription("desc"+random);
        role.setReferenceId("ref"+random);
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setAssignableType(ReferenceType.APPLICATION);
        role.setOauthScopes(Arrays.asList("scope1"+random, "scope2"+random));
        role.setCreatedAt(new Date());
        role.setUpdatedAt(new Date());
        Map<Permission, Set<Acl>> permissions = new HashMap<>();
        permissions.put(Permission.APPLICATION, Sets.newSet(Acl.CREATE));
        role.setPermissionAcls(permissions);
        return role;
    }

    private void assertEqualsTo(Role role, TestObserver<Role> testObserver) {
        testObserver.assertValue(r -> r.getName().equals(role.getName()));
        testObserver.assertValue(r -> r.getAssignableType().equals(role.getAssignableType()));
        testObserver.assertValue(r -> r.getDescription().equals(role.getDescription()));
        testObserver.assertValue(r -> r.getReferenceId().equals(role.getReferenceId()));
        testObserver.assertValue(r -> r.getReferenceType().equals(role.getReferenceType()));
        testObserver.assertValue(r -> r.getOauthScopes().containsAll(role.getOauthScopes()));
        testObserver.assertValue(r -> r.getPermissionAcls().keySet().containsAll(role.getPermissionAcls().keySet()));
        testObserver.assertValue(r -> r.getPermissionAcls().get(Permission.APPLICATION).containsAll(role.getPermissionAcls().get(Permission.APPLICATION)));
    }

    @Test
    public void testFindById_referenceType() {
        // create role
        Role role = buildRole();
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setReferenceId(DOMAIN_ID);
        Role roleCreated = roleRepository.create(role).blockingGet();

        // fetch role
        TestObserver<Role> testObserver = roleRepository.findById(ReferenceType.DOMAIN, DOMAIN_ID, roleCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(role, testObserver);
    }

    @Test
    public void testFindAll() {
        // create role
        Role role1 = new Role();
        role1.setName("testName1");
        role1.setReferenceType(ReferenceType.DOMAIN);
        role1.setReferenceId(DOMAIN_ID);
        Role roleCreated1 = roleRepository.create(role1).blockingGet();

        Role role2 = new Role();
        role2.setName("testName2");
        role2.setReferenceType(ReferenceType.DOMAIN);
        role2.setReferenceId(DOMAIN_ID);
        Role roleCreated2 = roleRepository.create(role2).blockingGet();

        // Role 3 is on domain#2.
        Role role3 = new Role();
        role3.setName("testName3");
        role3.setReferenceType(ReferenceType.DOMAIN);
        role3.setReferenceId("domain#2");
        roleRepository.create(role3).blockingGet();

        // fetch role
        TestSubscriber<Role> testObserver = roleRepository.findAll(ReferenceType.DOMAIN, DOMAIN_ID).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(2);
        List<Role> roles = testObserver.values();
        assertTrue(roles.stream().anyMatch(role -> role.getId().equals(roleCreated1.getId())));
        assertTrue(roles.stream().anyMatch(role -> role.getId().equals(roleCreated2.getId())));
    }

    @Test
    public void testNotFoundById() throws Exception {
        TestObserver<Role> test = roleRepository.findById("test").test();
        test.await(10, TimeUnit.SECONDS);
        test.assertNoValues();
    }

    @Test
    public void testCreate() {
        Role role = new Role();
        role.setName(TEST_ROLE_NAME);
        TestObserver<Role> testObserver = roleRepository.create(role).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(r -> r.getName().equals(role.getName()));
    }

    @Test
    public void testUpdate() {
        // create role
        Role role = new Role();
        role.setName(TEST_ROLE_NAME);
        Role roleCreated = roleRepository.create(role).blockingGet();

        // update role
        Role updatedRole = new Role();
        updatedRole.setId(roleCreated.getId());
        updatedRole.setName("testUpdatedName");

        TestObserver<Role> testObserver = roleRepository.update(updatedRole).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(r -> r.getName().equals(updatedRole.getName()));
    }

    @Test
    public void testDelete() {
        // create role
        Role role = new Role();
        role.setName(TEST_ROLE_NAME);
        Role roleCreated = roleRepository.create(role).blockingGet();

        // fetch role
        TestObserver<Role> testObserver = roleRepository.findById(roleCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(r -> r.getName().equals(roleCreated.getName()));

        // delete role
        TestObserver<Void> testObserver1 = roleRepository.delete(roleCreated.getId()).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch role
        testObserver = roleRepository.findById(roleCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }

    @Test
    public void testFilterOutProtectedResourceRoles() throws Exception {
        Role normalRole = new Role();
        normalRole.setName("normalRole");
        normalRole.setReferenceType(ReferenceType.DOMAIN);
        normalRole.setReferenceId(DOMAIN_ID);
        Role normalRoleCreated = roleRepository.create(normalRole).blockingGet();
        
        insertIncompatibleRoleDirectly("incompatibleRole", FUTURE_REFERENCE_TYPE, null, DOMAIN_ID);
        insertIncompatibleRoleDirectly("incompatibleRole2", ReferenceType.DOMAIN.name(), FUTURE_ASSIGNABLE_TYPE, DOMAIN_ID);
        TestSubscriber<Role> testObserver = roleRepository.findAll(ReferenceType.DOMAIN, DOMAIN_ID).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        
        List<Role> roles = testObserver.values();
        assertTrue("Should return the normal role", 
                roles.stream().anyMatch(role -> role.getId().equals(normalRoleCreated.getId())));
        assertEquals("Should return exactly 1 role (incompatible ones filtered)", 1, roles.size());
        assertFalse("Incompatible role with future referenceType should be filtered out",
                roles.stream().anyMatch(role -> "incompatibleRole".equals(role.getName())));
        assertFalse("Incompatible role with future assignableType should be filtered out",
                roles.stream().anyMatch(role -> "incompatibleRole2".equals(role.getName())));
    }

    @Test
    public void testFilterOutProtectedResourceRoles_NoCrash() throws Exception {
        insertIncompatibleRoleDirectly("testFutureEnumRole", FUTURE_REFERENCE_TYPE, null, "test-id");
        
        TestSubscriber<Role> testObserver = roleRepository.findAll(ReferenceType.DOMAIN, DOMAIN_ID).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        List<Role> roles = testObserver.values();
        assertFalse("Role containing future referenceType should be filtered out",
                roles.stream().anyMatch(role -> "testFutureEnumRole".equals(role.getName())));
    }

    @Test
    public void testFilterOutProtectedResourceRole_FindById() throws Exception {
        String incompatibleRoleId = insertIncompatibleRoleDirectlyAndGetId("testFutureEnumRoleFindById", FUTURE_REFERENCE_TYPE, null, "test-id");
        TestObserver<Role> testObserver = roleRepository.findById(incompatibleRoleId).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }
    
    private void insertIncompatibleRoleDirectly(String roleName, String referenceType, String assignableType, String referenceId) throws Exception {
        insertIncompatibleRoleDirectlyAndGetId(roleName, referenceType, assignableType, referenceId);
    }
    
    private String insertIncompatibleRoleDirectlyAndGetId(String roleName, String referenceType, String assignableType, String referenceId) throws Exception {
        String repoClassName = roleRepository.getClass().getSimpleName();
        String repoFullName = roleRepository.getClass().getName();
        
        if (repoClassName.contains("Mongo") || repoFullName.contains("mongodb")) {
            return insertIncompatibleRoleMongoDB(roleName, referenceType, assignableType, referenceId);
        } else if (repoClassName.contains("Jdbc") || repoFullName.contains("jdbc")) {
            return insertIncompatibleRoleJDBC(roleName, referenceType, assignableType, referenceId);
        } else {
            throw new UnsupportedOperationException("Unknown repository type: " + repoClassName + " (" + repoFullName + ")");
        }
    }
    
    private String insertIncompatibleRoleMongoDB(String roleName, String referenceType, String assignableType, String referenceId) throws Exception {
        return IncompatibleDataTestUtils.insertIncompatibleEntityMongoDB(
            roleRepository,
            "roles",
            "io.gravitee.am.repository.mongodb.management.internal.model.RoleMongo",
            roleMongo -> {
                ReflectionTestUtils.setField(roleMongo, "name", roleName);
                ReflectionTestUtils.setField(roleMongo, "referenceType", referenceType);
                ReflectionTestUtils.setField(roleMongo, "assignableType", assignableType);
                ReflectionTestUtils.setField(roleMongo, "referenceId", referenceId);
                ReflectionTestUtils.setField(roleMongo, "system", false);
                ReflectionTestUtils.setField(roleMongo, "defaultRole", false);
            }
        );
    }
    
    private String insertIncompatibleRoleJDBC(String roleName, String referenceType, String assignableType, String referenceId) throws Exception {
        return IncompatibleDataTestUtils.insertIncompatibleEntityJDBC(
            roleRepository,
            "io.gravitee.am.repository.jdbc.management.api.model.JdbcRole",
            jdbcRole -> {
                ReflectionTestUtils.setField(jdbcRole, "name", roleName);
                ReflectionTestUtils.setField(jdbcRole, "referenceType", referenceType);
                ReflectionTestUtils.setField(jdbcRole, "assignableType", assignableType);
                ReflectionTestUtils.setField(jdbcRole, "referenceId", referenceId);
                ReflectionTestUtils.setField(jdbcRole, "system", false);
                ReflectionTestUtils.setField(jdbcRole, "defaultRole", false);
                ReflectionTestUtils.setField(jdbcRole, "description", null);
                ReflectionTestUtils.setField(jdbcRole, "permissionAcls", null);
                ReflectionTestUtils.setField(jdbcRole, "createdAt", null);
                ReflectionTestUtils.setField(jdbcRole, "updatedAt", null);
            }
        );
    }
}
