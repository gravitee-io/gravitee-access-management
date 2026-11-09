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
package io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.AuthorizationDetailTypeEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidAuthorizationDetailsException;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationAuthorizationDetailTypeSettings;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oauth2.AuthorizationDetail;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.event.Event;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationDetailsValidatorTest {

    private static final String PAYMENT = "payment_initiation";
    private static final String ACCOUNT = "account_information";
    private static final String NOT_ENABLED = "internal_transfer";
    private static final String DOMAIN_ID = "domain-id";
    private static final String VALID_PAYMENT = "[{\"type\":\"payment_initiation\",\"instructedAmount\":{\"currency\":\"EUR\",\"amount\":\"1\"}}]";
    private static final String PAYMENT_SCHEMA = """
            {
              "type": "object",
              "required": ["type", "instructedAmount"],
              "properties": {
                "type": { "const": "payment_initiation" },
                "instructedAmount": {
                  "type": "object",
                  "required": ["currency", "amount"],
                  "properties": {
                    "currency": { "type": "string", "pattern": "^[A-Z]{3}$" },
                    "amount": { "type": "string" }
                  }
                }
              }
            }""";

    private static final Map<String, Object> GRANTED_PAYMENT = Map.of("type", PAYMENT, "actions", List.of("initiate", "status"));
    private static final Map<String, Object> GRANTED_ACCOUNT = Map.of("type", ACCOUNT, "locations", List.of("https://bank.example.com"));
    private static final AuthorizationDetails GRANTED = AuthorizationDetails.of(List.of(GRANTED_PAYMENT, GRANTED_ACCOUNT));

    private final Map<String, AuthorizationDetailType> domainTypes = new HashMap<>();
    private final AuthorizationDetailTypeManager manager = mock(AuthorizationDetailTypeManager.class);
    private final EventManager eventManager = mock(EventManager.class);
    private final Domain domain = new Domain(DOMAIN_ID);
    private final AuthorizationDetailsValidator validator = new AuthorizationDetailsValidator(manager, eventManager, domain);
    private final Client client = new Client();

    @BeforeEach
    void setUp() {
        when(manager.findByType(anyString())).thenAnswer(invocation -> Optional.ofNullable(domainTypes.get(invocation.<String>getArgument(0))));
        domainType(PAYMENT, PAYMENT_SCHEMA);
        domainType(ACCOUNT, null);
        domainType(NOT_ENABLED, null);
        client.setAuthorizationDetailTypes(enabled(PAYMENT, ACCOUNT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[{\"type\":\"account_information\"}", "[{\"type\":\"account_information\",\"type\":\"account_information\"}]",
            "[{\"type\":\"account_information\"}] []"})
    void shouldRejectInvalidJson(String authorizationDetails) {
        assertRejected(authorizationDetails, InvalidRequestException.class, "authorization_details is not valid JSON");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"type\":\"account_information\"}", "[]", "\"account_information\"", "null"})
    void shouldRejectAnythingButANonEmptyArray(String authorizationDetails) {
        assertRejected(authorizationDetails, InvalidRequestException.class, "authorization_details must be a non-empty JSON array");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"account_information\"", "[]", "null", "42"})
    void shouldRejectAnElementThatIsNotAnObject(String element) {
        assertRejected("[{\"type\":\"account_information\"}," + element + "]", InvalidRequestException.class,
                "authorization_details[1] must be a JSON object");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"type\":\"\"}", "{\"type\":42}", "{\"type\":null}", "{\"type\":[\"account_information\"]}"})
    void shouldRejectAnElementWithoutAStringType(String element) {
        assertRejected("[" + element + "]", InvalidRequestException.class, "authorization_details[0] must have a non-empty string type");
    }

    @Test
    void shouldReportSyntaxBeforeSemantics() {
        assertRejected("[{\"type\":\"unknown\"},{}]", InvalidRequestException.class, "authorization_details[1] must have a non-empty string type");
    }

    @Test
    void shouldRejectATypeTheDomainDoesNotHave() {
        client.setAuthorizationDetailTypes(enabled(PAYMENT, ACCOUNT, "unknown"));

        assertRejected("[{\"type\":\"unknown\"}]", InvalidAuthorizationDetailsException.class,
                "authorization_details[0] of type 'unknown' is not allowed for this client");
    }

    @Test
    void shouldMatchTypesCaseSensitively() {
        assertRejected("[{\"type\":\"Account_Information\"}]", InvalidAuthorizationDetailsException.class,
                "authorization_details[0] of type 'Account_Information' is not allowed for this client");
    }

    @Test
    void shouldRejectATypeNotEnabledOnTheClient() {
        assertRejected("[{\"type\":\"account_information\"},{\"type\":\"internal_transfer\"}]", InvalidAuthorizationDetailsException.class,
                "authorization_details[1] of type 'internal_transfer' is not allowed for this client");
    }

    @Test
    void shouldRejectEveryTypeWhenTheClientEnablesNone() {
        client.setAuthorizationDetailTypes(null);

        assertRejected("[{\"type\":\"account_information\"}]", InvalidAuthorizationDetailsException.class,
                "authorization_details[0] of type 'account_information' is not allowed for this client");
    }

    @Test
    void shouldRejectAnElementItsSchemaRejectsWithoutRepeatingItsValues() {
        String authorizationDetails = "[{\"type\":\"account_information\"},"
                + "{\"type\":\"payment_initiation\",\"instructedAmount\":{\"currency\":\"euros\",\"amount\":\"123.50\"}}]";

        assertThatThrownBy(() -> validator.validate(authorizationDetails, client))
                .isInstanceOf(InvalidAuthorizationDetailsException.class)
                .hasMessage("authorization_details[1] of type 'payment_initiation' does not match its schema")
                .hasMessageNotContainingAny("euros", "123.50", "instructedAmount");
    }

    @ParameterizedTest
    @ValueSource(strings = {"pay\\\"ment", "pay\\\\ment", "pay'ment", "paiement_é"})
    void shouldNameOnlyThePositionOfATypeThatCannotAppearInAnErrorDescription(String type) {
        assertRejected("[{\"type\":\"" + type + "\"}]", InvalidAuthorizationDetailsException.class,
                "authorization_details[0] is not allowed for this client");
    }

    @Test
    void shouldRejectEveryElementOfATypeWhoseSchemaCannotBeUsed() {
        domainType(ACCOUNT, "{\"type\": \"object\", \"$ref\": \"#/$defs/missing\"}");

        assertRejected("[{\"type\":\"account_information\"}]", InvalidAuthorizationDetailsException.class,
                "authorization_details[0] of type 'account_information' does not match its schema");
    }

    @Test
    void shouldApplyAnUpdatedSchema() {
        String element = "[{\"type\":\"account_information\",\"actions\":[\"read\"]}]";
        domainType(ACCOUNT, "{\"type\":\"object\",\"properties\":{\"type\":{\"const\":\"account_information\"}},\"required\":[\"type\"]}");
        validator.validate(element, client);

        domainType(ACCOUNT, "{\"type\":\"object\",\"properties\":{\"type\":{\"const\":\"account_information\"}},\"required\":[\"type\"],"
                + "\"additionalProperties\":false}");

        assertRejected(element, InvalidAuthorizationDetailsException.class,
                "authorization_details[0] of type 'account_information' does not match its schema");
    }

    @Test
    void shouldListenForTypeEventsUntilStopped() throws Exception {
        validator.afterPropertiesSet();
        validator.stop();

        verify(eventManager).subscribeForEvents(validator, AuthorizationDetailTypeEvent.class, DOMAIN_ID);
        verify(eventManager).unsubscribeForEvents(validator, AuthorizationDetailTypeEvent.class, DOMAIN_ID);
    }

    @Test
    void shouldDropTheCompiledSchemaOfADeletedType() {
        validator.validate(VALID_PAYMENT, client);

        validator.onEvent(event(AuthorizationDetailTypeEvent.UNDEPLOY, PAYMENT + "-id", DOMAIN_ID));

        assertThat(validator.schemas).isEmpty();
    }

    @Test
    void shouldKeepTheCompiledSchemaOnOtherEvents() {
        validator.validate(VALID_PAYMENT, client);

        validator.onEvent(event(AuthorizationDetailTypeEvent.UPDATE, PAYMENT + "-id", DOMAIN_ID));
        validator.onEvent(event(AuthorizationDetailTypeEvent.UNDEPLOY, PAYMENT + "-id", "other-domain"));
        validator.onEvent(event(AuthorizationDetailTypeEvent.UNDEPLOY, ACCOUNT + "-id", DOMAIN_ID));

        assertThat(validator.schemas).containsOnlyKeys(PAYMENT + "-id");
    }

    @Test
    void shouldAcceptAnyObjectOfATypeWithoutSchema() {
        List<Map<String, Object>> details = validator.validate("[{\"type\":\"account_information\",\"anything\":[1,{\"nested\":true}]}]", client).getRaw();

        assertThat(details).containsExactly(Map.of("type", ACCOUNT, "anything", List.of(1, Map.of("nested", true))));
    }

    @Test
    void shouldAcceptSeveralElementsAndReturnThemUnchanged() {
        String authorizationDetails = "[{\"type\":\"payment_initiation\",\"instructedAmount\":{\"currency\":\"EUR\",\"amount\":\"123.50\"},\"rate\":0.10000000000000000001},"
                + "{\"type\":\"account_information\",\"actions\":[\"list_accounts\",\"read_balances\"]},"
                + "{\"type\":\"payment_initiation\",\"instructedAmount\":{\"currency\":\"USD\",\"amount\":\"9\"}}]";

        List<Map<String, Object>> details = validator.validate(authorizationDetails, client).getRaw();

        assertThat(details).hasSize(3);
        assertThat(details.get(0)).containsExactly(
                Map.entry("type", PAYMENT),
                Map.entry("instructedAmount", Map.of("currency", "EUR", "amount", "123.50")),
                Map.entry("rate", new BigDecimal("0.10000000000000000001")));
        assertThat(details.get(1)).isEqualTo(Map.of("type", ACCOUNT, "actions", List.of("list_accounts", "read_balances")));
        assertThat(details.get(2)).isEqualTo(Map.of("type", PAYMENT, "instructedAmount", Map.of("currency", "USD", "amount", "9")));
    }

    @Test
    void shouldReturnTypedElementsGroupedByType() {
        AuthorizationDetails details = validator.validate("[{\"type\":\"account_information\",\"actions\":[\"list_accounts\"]},"
                + "{\"type\":\"payment_initiation\",\"instructedAmount\":{\"currency\":\"EUR\",\"amount\":\"1\"}}]", client);

        assertThat(details.getAll()).extracting(AuthorizationDetail::getType).containsExactly(ACCOUNT, PAYMENT);
        assertThat(details.getByType().get(ACCOUNT)).singleElement()
                .extracting(AuthorizationDetail::getActions).isEqualTo(List.of("list_accounts"));
        assertThat(details.getByType().get(PAYMENT)).singleElement()
                .extracting(detail -> detail.get("instructedAmount")).isEqualTo(Map.of("currency", "EUR", "amount", "1"));
    }

    @Test
    void shouldReturnNullWhenATokenRequestHasNoAuthorizationDetails() {
        assertThat(validator.validate(tokenRequest(null), client)).isNull();
    }

    @Test
    void shouldValidateTheAuthorizationDetailsOfATokenRequest() {
        assertThat(validator.validate(tokenRequest(VALID_PAYMENT), client).getRaw())
                .containsExactly(Map.of("type", PAYMENT, "instructedAmount", Map.of("currency", "EUR", "amount", "1")));
        assertThatThrownBy(() -> validator.validate(tokenRequest("[{\"type\":\"internal_transfer\"}]"), client))
                .isInstanceOf(InvalidAuthorizationDetailsException.class)
                .hasMessage("authorization_details[0] of type 'internal_transfer' is not allowed for this client");
    }

    @Test
    void shouldAcceptNothingRequestedWhateverWasGranted() {
        assertThatCode(() -> validator.validateGranted(null, GRANTED)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateGranted(null, null)).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptTheGrantedDetailsInAnyOrder() {
        assertThatCode(() -> validator.validateGranted(AuthorizationDetails.of(List.of(GRANTED_ACCOUNT, GRANTED_PAYMENT)), GRANTED))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldIgnoreMemberOrderWithinAGrantedElement() {
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("actions", List.of("initiate", "status"));
        reordered.put("type", PAYMENT);

        assertThatCode(() -> validator.validateGranted(AuthorizationDetails.of(List.of(reordered, GRANTED_ACCOUNT)), GRANTED))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("detailsDifferentFromGranted")
    void shouldRejectDetailsThatDifferFromTheGrantedOnes(String difference, List<Map<String, Object>> requested) {
        assertThatThrownBy(() -> validator.validateGranted(AuthorizationDetails.of(requested), GRANTED))
                .isInstanceOf(InvalidAuthorizationDetailsException.class)
                .hasMessage("authorization_details do not match the granted authorization details");
    }

    static Stream<Arguments> detailsDifferentFromGranted() {
        return Stream.of(
                Arguments.of("subset", List.of(GRANTED_PAYMENT)),
                Arguments.of("extra element", List.of(GRANTED_PAYMENT, GRANTED_ACCOUNT, Map.of("type", NOT_ENABLED))),
                Arguments.of("duplicated element", List.of(GRANTED_PAYMENT, GRANTED_ACCOUNT, GRANTED_PAYMENT)),
                Arguments.of("changed value", List.of(Map.of("type", PAYMENT, "actions", List.of("initiate")), GRANTED_ACCOUNT)),
                Arguments.of("reordered nested array", List.of(Map.of("type", PAYMENT, "actions", List.of("status", "initiate")), GRANTED_ACCOUNT)),
                Arguments.of("replaced element", List.of(GRANTED_PAYMENT, Map.of("type", ACCOUNT))));
    }

    @Test
    void shouldRejectDetailsWhenNoneWereGranted() {
        assertThatThrownBy(() -> validator.validateGranted(GRANTED, null))
                .isInstanceOf(InvalidAuthorizationDetailsException.class)
                .hasMessage("no authorization_details were granted");
    }

    private static TokenRequest tokenRequest(String authorizationDetails) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        if (authorizationDetails != null) {
            parameters.add(Parameters.AUTHORIZATION_DETAILS, authorizationDetails);
        }
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setParameters(parameters);
        return tokenRequest;
    }

    private void assertRejected(String authorizationDetails, Class<? extends OAuth2Exception> error, String description) {
        assertThatThrownBy(() -> validator.validate(authorizationDetails, client))
                .isInstanceOf(error)
                .hasMessage(description);
    }

    @SuppressWarnings("unchecked")
    private static Event<AuthorizationDetailTypeEvent, Payload> event(AuthorizationDetailTypeEvent type, String id, String domainId) {
        Event<AuthorizationDetailTypeEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(type);
        when(event.content()).thenReturn(new Payload(id, ReferenceType.DOMAIN, domainId, Action.DELETE));
        return event;
    }

    private void domainType(String type, String schema) {
        domainTypes.put(type, AuthorizationDetailType.builder().id(type + "-id").type(type).schema(schema).build());
    }

    private static List<ApplicationAuthorizationDetailTypeSettings> enabled(String... types) {
        return Arrays.stream(types).map(ApplicationAuthorizationDetailTypeSettings::new).toList();
    }
}
