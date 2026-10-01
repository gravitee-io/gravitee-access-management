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
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.management.service.AbstractSensitiveProxy;
import io.gravitee.am.management.service.MaskingMode;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
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

    private static final String PROBED_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "uri": { "type": "string", "sensitive-uri": true },
                "password": { "type": "string", "sensitive": true },
                "label": { "type": "string" },
                "callbackUri": { "type": "string" }
              }
            }
            """;

    private static final String NESTED_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "ldapConfig": {
                  "type": "object",
                  "properties": {
                    "url": { "type": "string" },
                    "password": { "type": "string", "sensitive": true }
                  }
                }
              }
            }
            """;

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
        filterSensitiveData(schema, config, maskedConfig -> assertUriEquals("URI should be the same", URI_WITHOUT_USERINFO, maskedConfig));
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

    @Test
    public void shouldReject_URI_withMaskedPassword() {
        rejectMasked("{\"uri\": \"" + URI_WITH_MASKED_PWD + "\"}")
                .test()
                .assertError(error -> error instanceof InvalidParameterException
                        && error.getMessage().contains("'configuration/uri' holds a masked password"));
    }

    @Test
    public void shouldReject_URI_withMaskedPassword_ofAnyLength() {
        rejectMasked("{\"uri\": \"mongodb://user:***@hostname/dbname\"}")
                .test()
                .assertError(error -> error.getMessage().contains("configuration/uri"));
    }

    @Test
    public void shouldReject_maskedSensitiveValue() {
        rejectMasked("{\"password\": \"" + SENSITIVE_VALUE + "\"}")
                .test()
                .assertError(error -> error instanceof InvalidParameterException
                        && error.getMessage().contains("'configuration/password' holds the masked value '********'"));
    }

    @Test
    public void shouldAccept_URI_withPassword() {
        rejectMasked("{\"uri\": \"" + URI_WITH_CREDENTIALS + "\"}").test().assertComplete();
    }

    @Test
    public void shouldAccept_URI_withoutPassword() {
        rejectMasked("{\"uri\": \"" + URI_WITH_USERNAME + "\"}").test().assertComplete();
    }

    @Test
    public void shouldAccept_URI_withoutUserinfo() {
        rejectMasked("{\"uri\": \"" + URI_WITHOUT_USERINFO + "\"}").test().assertComplete();
    }

    @Test
    public void shouldAccept_realSensitiveValue() {
        rejectMasked("{\"password\": \"s3cr3t\"}").test().assertComplete();
    }

    @Test
    public void shouldAccept_maskInFieldsThatAreNotSensitive() {
        rejectMasked("{\"label\": \"" + SENSITIVE_VALUE + "\", \"callbackUri\": \"" + URI_WITH_MASKED_PWD + "\"}")
                .test()
                .assertComplete();
    }

    @Test
    public void shouldAccept_maskInFieldTheMaskingDrops() {
        rejectMaskedSensitiveValues("{\"password\": \"" + SENSITIVE_VALUE + "\"}", configuration -> Single.just("{}"))
                .test()
                .assertComplete();
    }

    @Test
    public void shouldAccept_malformedConfiguration() {
        rejectMasked("{not json").test().assertComplete();
    }

    @Test
    public void shouldReject_withThePathOfAMaskedValueInsideAnArray() {
        String configuration = "{\"users\":[{\"username\":\"alice\",\"password\":\"s3cr3t\"},{\"username\":\"bob\",\"password\":\"***\"}]}";

        rejectMaskedSensitiveValues(configuration, this::maskInlineUserPasswords)
                .test()
                .assertError(error -> error.getMessage().contains("'configuration/users/1/password'"));
    }

    @Test
    public void shouldReject_withThePathOfAMaskedValueInsideANestedObject() {
        String configuration = "{\"ldapConfig\":{\"url\":\"ldap://ldap:389\",\"password\":\"" + SENSITIVE_VALUE + "\"}}";

        rejectMaskedSensitiveValues(configuration, probe -> Single.fromCallable(() -> {
            JsonNode node = objectMapper.readTree(probe);
            filterNestedSensitiveData(objectMapper.readTree(NESTED_SCHEMA), node, "/properties/ldapConfig", "/ldapConfig");
            return node.toString();
        }))
                .test()
                .assertError(error -> error.getMessage().contains("'configuration/ldapConfig/password' holds the masked value"));
    }

    @Test
    public void shouldReject_withAnEscapedPathForAFieldNameHoldingSlashOrTilde() {
        rejectMaskedSensitiveValues("{\"client/secret~1\":\"" + SENSITIVE_VALUE + "\"}", probe -> Single.just("{\"client/secret~1\":\"********\"}"))
                .test()
                .assertError(error -> error.getMessage().contains("'configuration/client~1secret~01'"));
    }

    @Test
    public void shouldReject_withThePathOfAMaskedStringInsideAnArray() {
        rejectMaskedSensitiveValues("{\"keys\":[\"k1\",\"" + SENSITIVE_VALUE + "\"]}", probe -> Single.just("{\"keys\":[\"********\",\"********\"]}"))
                .test()
                .assertError(error -> error.getMessage().contains("'configuration/keys/1' holds the masked value"));
    }

    @Test
    public void shouldReject_theFirstMaskedValueInDocumentOrder() {
        rejectMasked("{\"uri\": \"" + URI_WITH_MASKED_PWD + "\", \"password\": \"" + SENSITIVE_VALUE + "\"}")
                .test()
                .assertError(error -> error.getMessage().contains("'configuration/uri'"));
    }

    private Completable rejectMasked(String configuration) {
        return rejectMaskedSensitiveValues(configuration, probe -> Single.fromCallable(() -> {
            String[] masked = {probe};
            filterSensitiveData(objectMapper.readTree(PROBED_SCHEMA), objectMapper.readTree(probe), maskedConfig -> masked[0] = maskedConfig);
            return masked[0];
        }));
    }

    private Single<String> maskInlineUserPasswords(String configuration) {
        return Single.fromCallable(() -> {
            JsonNode node = objectMapper.readTree(configuration);
            node.get("users").forEach(user -> ((ObjectNode) user).put("password", SENSITIVE_VALUE));
            return node.toString();
        });
    }

    @Test
    public void shouldMaskAbsentSensitiveValue_whenMaskingAlways() throws Exception {
        assertMasked(MaskingMode.ALWAYS, "{\"label\":\"x\"}", "{\"label\":\"x\",\"password\":\"" + SENSITIVE_VALUE + "\"}");
    }

    @Test
    public void shouldOmitAbsentSensitiveValues_whenMaskingPresentOnly() throws Exception {
        assertMasked(MaskingMode.PRESENT_ONLY, "{\"label\":\"x\"}", "{\"label\":\"x\"}");
    }

    @Test
    public void shouldOmitNullSensitiveValues_whenMaskingPresentOnly() throws Exception {
        assertMasked(MaskingMode.PRESENT_ONLY, "{\"label\":\"x\",\"password\":null,\"uri\":null}", "{\"label\":\"x\"}");
    }

    @Test
    public void shouldKeepEmptySensitiveValue_whenMaskingPresentOnly() throws Exception {
        assertMasked(MaskingMode.PRESENT_ONLY, "{\"password\":\"\"}", "{\"password\":\"\"}");
    }

    @Test
    public void shouldMaskSetSensitiveValue_whenMaskingPresentOnly() throws Exception {
        assertMasked(MaskingMode.PRESENT_ONLY, "{\"password\":\"secret\"}", "{\"password\":\"" + SENSITIVE_VALUE + "\"}");
    }

    @Test
    public void shouldMaskOnlyTheUriPassword_whenMaskingPresentOnly() throws Exception {
        assertMasked(MaskingMode.PRESENT_ONLY, "{\"uri\":\"" + URI_WITH_CREDENTIALS + "\"}", "{\"uri\":\"" + URI_WITH_MASKED_PWD + "\"}");
        assertMasked(MaskingMode.PRESENT_ONLY, "{\"uri\":\"" + URI_WITH_USERNAME + "\"}", "{\"uri\":\"" + URI_WITH_USERNAME + "\"}");
    }

    @Test
    public void shouldOmitAbsentNestedSensitiveValue_whenMaskingPresentOnly() throws Exception {
        JsonNode config = objectMapper.readTree("{\"ldapConfig\":{\"url\":\"ldap://ldap:389\"}}");

        filterNestedSensitiveData(objectMapper.readTree(NESTED_SCHEMA), config, "/properties/ldapConfig", "/ldapConfig", MaskingMode.PRESENT_ONLY);

        assertEquals(objectMapper.readTree("{\"ldapConfig\":{\"url\":\"ldap://ldap:389\"}}"), config);
    }

    private void assertMasked(MaskingMode mode, String configuration, String expected) throws Exception {
        String[] masked = new String[1];
        filterSensitiveData(objectMapper.readTree(PROBED_SCHEMA), objectMapper.readTree(configuration), maskedConfig -> masked[0] = maskedConfig, mode);
        assertEquals(objectMapper.readTree(expected), objectMapper.readTree(masked[0]));
    }

    private void assertUriEquals(String message, String expectedValue, String processedConfig) {
        try {
            assertEquals(message, expectedValue, (String)objectMapper.readValue(processedConfig, Map.class).get("uri"));
        } catch (Exception e) {
            fail();
        }
    }

}
