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
package io.gravitee.am.management.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.service.AbstractSensitiveProxy;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */

public class SensitiveDataMaskingTest extends AbstractSensitiveProxy {

    private static final String URI_WITHOUT_USERINFO = "mongodb+srv://hostname/dbname?retryWrites=true&w=majority&connectTimeoutMS=10000&maxIdleTimeMS=30000";

    private static final String URI_WITH_USERNAME = "mongodb+srv://user@hostname/dbname?retryWrites=true&w=majority&connectTimeoutMS=10000&maxIdleTimeMS=30000";

    private static final String URI_WITH_USERNAME_AND_MULTIPLE_HOST = "mongodb+srv://user@hostname1,hostname2/dbname?retryWrites=true&w=majority&connectTimeoutMS=10000&maxIdleTimeMS=30000";

    private static final String URI_WITH_CREDENTIALS = "mongodb+srv://user:password@hostname/dbname?retryWrites=true&w=majority&connectTimeoutMS=10000&maxIdleTimeMS=30000";

    private static final String URI_WITH_CREDENTIALS_AND_MULTIPLE_HOST = "mongodb+srv://user:password@hostname1,hostname2/dbname?retryWrites=true&w=majority&connectTimeoutMS=10000&maxIdleTimeMS=30000";

    private static final String URI_WITH_CREDENTIALS_EMPTY_PWD = "mongodb+srv://user:@hostname/dbname?retryWrites=true&w=majority&connectTimeoutMS=10000&maxIdleTimeMS=30000";

    private static final String URI_WITH_UPDATED_CREDENTIALS = "mongodb+srv://user:password@hostname/dbname?retryWrites=true&w=majority&connectTimeoutMS=10000&maxIdleTimeMS=30000";

    private static final String URI_WITH_UPDATED_CREDENTIALS_AND_MULTIPLE_HOST = "mongodb+srv://user:password@hostname1,hostname2/dbname?retryWrites=true&w=majority&connectTimeoutMS=10000&maxIdleTimeMS=30000";

    private static final String URI_WITH_MASKED_PWD = "mongodb+srv://user:"+SENSITIVE_VALUE+"@hostname/dbname?retryWrites=true&w=majority&connectTimeoutMS=10000&maxIdleTimeMS=30000";

    private static final String URI_WITH_MASKED_PWD_AND_MULTIPLE_HOST = "mongodb+srv://user:"+SENSITIVE_VALUE+"@hostname1,hostname2/dbname?retryWrites=true&w=majority&connectTimeoutMS=10000&maxIdleTimeMS=30000";

    private static final String TESTABLE_SCHEMA = "{\n" +
            "  \"type\" : \"object\",\n" +
            "  \"id\" : \"urn:jsonschema:io:gravitee:am:identityprovider:mongo:MongoIdentityProviderConfiguration\",\n" +
            "  \"properties\" : {\n" +
            "    \"uri\" : {\n" +
            "      \"type\" : \"string\",\n" +
            "      \"default\": \"mongodb://localhost:27017\",\n" +
            "      \"title\": \"MongoDB connection URI\",\n" +
            "      \"description\": \"Connection URI used to connect to a MongoDB instance.\",\n" +
            "      \"sensitive-uri\": true\n" +
            "      }\n" +
            "    }\n" +
            "  }";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode schema = null;

    @Before
    public void init() throws Exception {
        this.schema = objectMapper.readTree(TESTABLE_SCHEMA);
    }

    @Test
    public void shouldNoFilter_NullURI() throws Exception {
        final JsonNode config = objectMapper.readTree("{}");
        filterSensitiveData(schema, config, (maskedConfig) -> {
            assertUriEquals("URI should be null", null, maskedConfig);
        });
    }

    @Test
    public void shouldNoFilter_EmptyURI() throws Exception {
        final JsonNode config = objectMapper.readTree("{ \"uri\" : \"\"}");
        filterSensitiveData(schema, config, (maskedConfig) -> {
            assertUriEquals("URI should be empty", "", maskedConfig);
        });
    }

    @Test
    public void shouldFilter_URI_withPassword() throws Exception {
        final JsonNode config = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_CREDENTIALS + "\"}");
        filterSensitiveData(schema, config, (maskedConfig) -> {
            assertUriEquals("Password must be replace by '*'", URI_WITH_MASKED_PWD, maskedConfig);
        });
    }


    @Test
    public void shouldFilter_URI_withPassword_and_multiple_host() throws Exception {
        final JsonNode config = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_CREDENTIALS_AND_MULTIPLE_HOST + "\"}");
        filterSensitiveData(schema, config, (maskedConfig) -> {
            assertUriEquals("Password must be replace by '*'", URI_WITH_MASKED_PWD_AND_MULTIPLE_HOST, maskedConfig);
        });
    }

    @Test
    public void shouldNoFilter_URI_withoutPassword() throws Exception {
        final JsonNode config = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_USERNAME + "\"}");
        filterSensitiveData(schema, config, (maskedConfig) -> {
            assertUriEquals("URI should be the same", URI_WITH_USERNAME, maskedConfig);
        });
    }

    @Test
    public void shouldNoFilter_URI_withoutPassword_and_multiHost() throws Exception {
        final JsonNode config = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_USERNAME_AND_MULTIPLE_HOST + "\"}");
        filterSensitiveData(schema, config, (maskedConfig) -> {
            assertUriEquals("URI should be the same", URI_WITH_USERNAME_AND_MULTIPLE_HOST, maskedConfig);
        });
    }

    @Test
    public void shouldNoFilter_URI_withoutUserInfo() throws Exception {
        final JsonNode config = objectMapper.readTree("{ \"uri\": \"" + URI_WITHOUT_USERINFO + "\"}");
        filterSensitiveData(schema, config, (maskedConfig) -> {
            assertUriEquals("URI should be the same", URI_WITHOUT_USERINFO, maskedConfig);
        });
    }

    @Test
    public void shouldUpdate_URI_withoutUserInfo() throws Exception {
        final JsonNode newConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITHOUT_USERINFO + "custom-param=toto\"}");
        final JsonNode oldConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITHOUT_USERINFO+ "\"}");

        updateSensitiveData(newConfig, oldConfig, schema, (uriToUpdate) -> {
            assertUriEquals("URI should be updated", URI_WITHOUT_USERINFO+ "custom-param=toto", uriToUpdate);
        });
    }

    @Test
    public void shouldUpdate_URI_withoutUserInfo_With_multipleWildcard() throws Exception {
        final JsonNode newConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITHOUT_USERINFO + "custom-param=*******\"}");
        final JsonNode oldConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITHOUT_USERINFO+ "\"}");

        updateSensitiveData(newConfig, oldConfig, schema, (uriToUpdate) -> {
            assertUriEquals("URI should be updated", URI_WITHOUT_USERINFO+ "custom-param=*******", uriToUpdate);
        });
    }

    @Test
    public void shouldUpdate_URI_withoutUserPassword() throws Exception {
        final JsonNode newConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_USERNAME + "custom-param=toto\"}");
        final JsonNode oldConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_USERNAME + "\"}");

        updateSensitiveData(newConfig, oldConfig, schema, (uriToUpdate) -> {
            assertUriEquals("URI should be updated", URI_WITH_USERNAME + "custom-param=toto", uriToUpdate);
        });
    }

    @Test
    public void shouldUpdate_URI_withoutUserPassword_With_multipleWildcard() throws Exception {
        final JsonNode newConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_USERNAME + "custom-param=*******\"}");
        final JsonNode oldConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_USERNAME + "\"}");

        updateSensitiveData(newConfig, oldConfig, schema, (uriToUpdate) -> {
            assertUriEquals("URI should be updated", URI_WITH_USERNAME + "custom-param=*******", uriToUpdate);
        });
    }

    @Test
    public void shouldUpdate_URI_withUserPassword_And_PreservePassword() throws Exception {
        // receive new URI (additional param) but with masked Password
        final JsonNode newConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_MASKED_PWD + "custom-param=toto\"}");
        final JsonNode oldConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_CREDENTIALS + "\"}");

        updateSensitiveData(newConfig, oldConfig, schema, (uriToUpdate) -> {
            // the final value must be the uri with password present into the old value
            assertUriEquals("URI should be updated with previous password", URI_WITH_CREDENTIALS + "custom-param=toto", uriToUpdate);
        });
    }

    @Test
    public void shouldUpdate_URI_withUserPassword_And_PreservePassword_And_multiple_host() throws Exception {
        // receive new URI (additional param) but with masked Password
        final JsonNode newConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_MASKED_PWD_AND_MULTIPLE_HOST + "custom-param=toto\"}");
        final JsonNode oldConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_CREDENTIALS_AND_MULTIPLE_HOST + "\"}");

        updateSensitiveData(newConfig, oldConfig, schema, (uriToUpdate) -> {
            // the final value must be the uri with password present into the old value
            assertUriEquals("URI should be updated with previous password", URI_WITH_CREDENTIALS_AND_MULTIPLE_HOST + "custom-param=toto", uriToUpdate);
        });
    }

    @Test
    public void shouldNotUpdate_URI_withUserPassword_IfNoChanges() throws Exception {
        // receive new URI (additional param) but with masked Password
        final JsonNode newConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_MASKED_PWD + "\"}");
        final JsonNode oldConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_CREDENTIALS + "\"}");

        updateSensitiveData(newConfig, oldConfig, schema, (uriToUpdate) -> {
            // the final value must be the uri with password present into the old value
            assertUriEquals("URI should not be updated", URI_WITH_CREDENTIALS, uriToUpdate);
        });
    }

    @Test
    public void shouldUpdate_URI_withUserPassword_And_UpdatePassword() throws Exception {
        // receive new URI (additional param) but with new Password
        final JsonNode newConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_UPDATED_CREDENTIALS + "custom-param=toto\"}");
        final JsonNode oldConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_CREDENTIALS + "\"}");

        updateSensitiveData(newConfig, oldConfig, schema, (uriToUpdate) -> {
            // the final value must be the new uri without change
            assertUriEquals("URI should be updated with the new value", URI_WITH_UPDATED_CREDENTIALS + "custom-param=toto", uriToUpdate);
        });
    }

    @Test
    public void shouldUpdate_URI_withUserPassword_And_UpdatePassword_with_multiHost() throws Exception {
        // receive new URI (additional param) but with new Password
        final JsonNode newConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_UPDATED_CREDENTIALS_AND_MULTIPLE_HOST + "custom-param=toto\"}");
        final JsonNode oldConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_CREDENTIALS_AND_MULTIPLE_HOST + "\"}");

        updateSensitiveData(newConfig, oldConfig, schema, (uriToUpdate) -> {
            // the final value must be the new uri without change
            assertUriEquals("URI should be updated with the new value", URI_WITH_UPDATED_CREDENTIALS_AND_MULTIPLE_HOST + "custom-param=toto", uriToUpdate);
        });
    }

    @Test
    public void shouldUpdate_URI_withUserEmptyPassword_And_UpdatePassword() throws Exception {
        // receive new URI (additional param) but with new Password
        final JsonNode newConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_CREDENTIALS_EMPTY_PWD + "custom-param=toto\"}");
        final JsonNode oldConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_CREDENTIALS + "\"}");

        updateSensitiveData(newConfig, oldConfig, schema, (uriToUpdate) -> {
            // the final value must be the new uri without change
            assertUriEquals("URI should be updated with the new value", URI_WITH_CREDENTIALS_EMPTY_PWD + "custom-param=toto", uriToUpdate);
        });
    }

    @Test
    public void shouldUpdate_URI_withUserPassword_Previous_Uri_Null() throws Exception {
        // receive new URI (additional param) but with masked Password
        final JsonNode newConfig = objectMapper.readTree("{ \"uri\": \"" + URI_WITH_CREDENTIALS + "custom-param=toto\"}");
        final JsonNode oldConfig = objectMapper.readTree("{}");

        updateSensitiveData(newConfig, oldConfig, schema, (uriToUpdate) -> {
            // the final value must be the uri with password present into the old value
            assertUriEquals("URI should be updated with previous password", URI_WITH_CREDENTIALS + "custom-param=toto", uriToUpdate);
        });
    }

    @Test
    public void shouldUpdate_URI_WithNullValue() throws Exception {
        // receive new URI (additional param) but with masked Password
        final JsonNode newConfig = objectMapper.readTree("{}");
        final JsonNode oldConfig = objectMapper.readTree("{\"uri\": \"" + URI_WITH_CREDENTIALS + "custom-param=toto\"}");

        updateSensitiveData(newConfig, oldConfig, schema, (uriToUpdate) -> {
            // the final value must be the uri with password present into the old value
            assertUriEquals("URI should be updated with null uri", null, uriToUpdate);
        });
    }

    private void assertUriEquals(String message, String expectedValue, String processedConfig) {
        try {
            assertEquals(message, expectedValue, (String)objectMapper.readValue(processedConfig, Map.class).get("uri"));
        } catch (Exception e) {
            fail();
        }
    }

}
