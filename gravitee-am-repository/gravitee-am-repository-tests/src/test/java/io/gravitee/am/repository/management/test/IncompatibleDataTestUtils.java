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
package io.gravitee.am.repository.management.test;

import org.springframework.test.util.ReflectionTestUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Utility class for inserting incompatible test data (e.g., PROTECTED_RESOURCE enum values)
 * directly into the database for backward compatibility testing.
 */
public class IncompatibleDataTestUtils {

    /**
     * Insert incompatible entity into MongoDB.
     */
    public static String insertIncompatibleEntityMongoDB(Object repository, String collectionName, 
                                                          String mongoEntityClass, Consumer<Object> fieldSetter) throws Exception {
        Object mongoOperations = ReflectionTestUtils.getField(repository, "mongoOperations");
        if (mongoOperations == null) {
            throw new IllegalStateException("mongoOperations is null - expected MongoDB repository");
        }

        Method getCollectionMethod = mongoOperations.getClass().getMethod("getCollection", String.class, Class.class);
        getCollectionMethod.setAccessible(true);
        Object collection = getCollectionMethod.invoke(mongoOperations, collectionName, Class.forName(mongoEntityClass));

        Class<?> entityClass = Class.forName(mongoEntityClass);
        Object entity = entityClass.getDeclaredConstructor().newInstance();

        String entityId = UUID.randomUUID().toString();
        ReflectionTestUtils.setField(entity, "id", entityId);
        fieldSetter.accept(entity);

        Method insertOneMethod = collection.getClass().getMethod("insertOne", Object.class);
        insertOneMethod.setAccessible(true);
        Object publisher = insertOneMethod.invoke(collection, entity);

        Class<?> publisherClass = Class.forName("org.reactivestreams.Publisher");
        Class<?> observableClass = Class.forName("io.reactivex.rxjava3.core.Observable");
        Method fromPublisherMethod = observableClass.getMethod("fromPublisher", publisherClass);
        fromPublisherMethod.setAccessible(true);
        Object observable = fromPublisherMethod.invoke(null, publisher);
        Method blockingFirstMethod = observableClass.getMethod("blockingFirst");
        blockingFirstMethod.setAccessible(true);
        blockingFirstMethod.invoke(observable);

        return entityId;
    }

    /**
     * Insert incompatible entity into JDBC database.
     */
    public static String insertIncompatibleEntityJDBC(Object repository, String jdbcEntityClass, 
                                                      Consumer<Object> fieldSetter) throws Exception {
        Class<?> r2dbcTemplateClass = Class.forName("org.springframework.data.r2dbc.core.R2dbcEntityTemplate");
        Object templateObj = ReflectionTestUtils.getField(repository, "template");
        if (templateObj == null || !r2dbcTemplateClass.isInstance(templateObj)) {
            throw new IllegalStateException("template is null or wrong type - expected R2dbcEntityTemplate");
        }

        Class<?> entityClass = Class.forName(jdbcEntityClass);
        Object entity = entityClass.getDeclaredConstructor().newInstance();

        String entityId = UUID.randomUUID().toString();
        ReflectionTestUtils.setField(entity, "id", entityId);
        fieldSetter.accept(entity);

        // Special handling for JdbcRole: MySQL reserved words and MSSQL strict types
        String jdbcRoleClassName = "io.gravitee.am.repository.jdbc.management.api.model.JdbcRole";
        if (jdbcRoleClassName.equals(jdbcEntityClass)) {
            String insertStatement = (String) ReflectionTestUtils.getField(repository, "insertStatement");
            if (insertStatement == null) {
                throw new IllegalStateException("insertStatement is null - expected JdbcRoleRepository");
            }

            MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
            Class<?> databaseClientInterface = Class.forName("org.springframework.r2dbc.core.DatabaseClient");
            MethodHandle getDatabaseClientHandle = publicLookup.findVirtual(r2dbcTemplateClass, "getDatabaseClient", MethodType.methodType(databaseClientInterface));
            
            Object databaseClient;
            try {
                databaseClient = getDatabaseClientHandle.invoke(templateObj);
            } catch (Throwable e) {
                throw new Exception("Failed to get database client", e);
            }

            Class<?> genericExecuteSpecClass = Class.forName("org.springframework.r2dbc.core.DatabaseClient$GenericExecuteSpec");
            MethodHandle sqlHandle = publicLookup.findVirtual(databaseClientInterface, "sql", MethodType.methodType(genericExecuteSpecClass, String.class));
            
            Object spec;
            try {
                spec = sqlHandle.invoke(databaseClient, insertStatement);
            } catch (Throwable e) {
                throw new Exception("Failed to create SQL spec", e);
            }

            // Explicit types required for MSSQL null handling
            spec = bindParameter(spec, "id", ReflectionTestUtils.getField(entity, "id"), String.class);
            spec = bindParameter(spec, "name", ReflectionTestUtils.getField(entity, "name"), String.class);
            spec = bindParameter(spec, "system", ReflectionTestUtils.getField(entity, "system"), Boolean.class);
            spec = bindParameter(spec, "default_role", ReflectionTestUtils.getField(entity, "defaultRole"), Boolean.class);
            spec = bindParameter(spec, "description", ReflectionTestUtils.getField(entity, "description"), String.class);
            spec = bindParameter(spec, "reference_id", ReflectionTestUtils.getField(entity, "referenceId"), String.class);
            spec = bindParameter(spec, "reference_type", ReflectionTestUtils.getField(entity, "referenceType"), String.class);
            spec = bindParameter(spec, "assignable_type", ReflectionTestUtils.getField(entity, "assignableType"), String.class);
            spec = bindParameter(spec, "permission_acls", ReflectionTestUtils.getField(entity, "permissionAcls"), Object.class);
            spec = bindParameter(spec, "created_at", ReflectionTestUtils.getField(entity, "createdAt"), LocalDateTime.class);
            spec = bindParameter(spec, "updated_at", ReflectionTestUtils.getField(entity, "updatedAt"), LocalDateTime.class);

            Method fetchMethod = spec.getClass().getMethod("fetch");
            fetchMethod.setAccessible(true);
            Object fetchSpec = fetchMethod.invoke(spec);
            Method rowsUpdatedMethod = fetchSpec.getClass().getMethod("rowsUpdated");
            rowsUpdatedMethod.setAccessible(true);
            Object mono = rowsUpdatedMethod.invoke(fetchSpec);
            blockMono(mono);
        } else {
            Method insertMethod = templateObj.getClass().getMethod("insert", Object.class);
            insertMethod.setAccessible(true);
            Object insertResult = insertMethod.invoke(templateObj, entity);
            blockMono(insertResult);
        }

        return entityId;
    }

    /**
     * Bind parameter with explicit type for MSSQL null handling.
     */
    private static Object bindParameter(Object spec, String name, Object value, Class<?> type) throws Exception {
        if (value == null) {
            Method bindNullMethod = spec.getClass().getMethod("bindNull", String.class, Class.class);
            bindNullMethod.setAccessible(true);
            return bindNullMethod.invoke(spec, name, type);
        } else {
            Method bindMethod = spec.getClass().getMethod("bind", String.class, Object.class);
            bindMethod.setAccessible(true);
            return bindMethod.invoke(spec, name, value);
        }
    }

    private static void blockMono(Object mono) throws Exception {
        Class<?> monoClass = Class.forName("reactor.core.publisher.Mono");
        Method blockMethod = monoClass.getMethod("block");
        blockMethod.setAccessible(true);
        blockMethod.invoke(mono);
    }
}
