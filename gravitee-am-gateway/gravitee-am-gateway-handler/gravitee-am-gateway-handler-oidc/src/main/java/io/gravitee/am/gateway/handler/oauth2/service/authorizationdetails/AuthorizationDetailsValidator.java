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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.event.AuthorizationDetailTypeEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidAuthorizationDetailsException;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.resource.SchemaLoader;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationAuthorizationDetailTypeSettings;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oauth2.AuthorizationDetail;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import lombok.CustomLog;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static io.gravitee.am.common.oauth2.Parameters.AUTHORIZATION_DETAILS;
import static io.gravitee.am.model.oauth2.AuthorizationDetailType.TYPE_MAX_LENGTH;

/**
 * See <a href="https://datatracker.ietf.org/doc/html/rfc9396#section-2">RFC 9396 Section 2</a>
 */
@CustomLog
public class AuthorizationDetailsValidator extends AbstractService implements InitializingBean,
        EventListener<AuthorizationDetailTypeEvent, Payload> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private static final TypeReference<List<AuthorizationDetail>> DETAILS = new TypeReference<>() {};

    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaLoader(SchemaLoader.getDefault()));

    // RFC 6749 error_description characters, without the quote used around the type
    private static final Pattern DESCRIBABLE_TYPE = Pattern.compile("^[\\x20-\\x21\\x23-\\x26\\x28-\\x5B\\x5D-\\x7E]+$");

    private final AuthorizationDetailTypeManager authorizationDetailTypeManager;

    private final EventManager eventManager;

    private final Domain domain;

    final ConcurrentMap<String, CompiledSchema> schemas = new ConcurrentHashMap<>();

    public AuthorizationDetailsValidator(AuthorizationDetailTypeManager authorizationDetailTypeManager, EventManager eventManager, Domain domain) {
        this.authorizationDetailTypeManager = authorizationDetailTypeManager;
        this.eventManager = eventManager;
        this.domain = domain;
    }

    @Override
    public void afterPropertiesSet() {
        eventManager.subscribeForEvents(this, AuthorizationDetailTypeEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        eventManager.unsubscribeForEvents(this, AuthorizationDetailTypeEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<AuthorizationDetailTypeEvent, Payload> event) {
        if (event.type() == AuthorizationDetailTypeEvent.UNDEPLOY
                && event.content().getReferenceType() == ReferenceType.DOMAIN
                && domain.getId().equals(event.content().getReferenceId())) {
            schemas.remove(event.content().getId());
        }
    }

    /**
     * Fails with {@link InvalidRequestException} when the value is malformed, or with {@link InvalidAuthorizationDetailsException}
     * when an element's type is unknown, not enabled on the client, or its schema rejects the element.
     *
     * @return the elements, in order and unchanged
     */
    public AuthorizationDetails validate(String authorizationDetails, Client client) {
        JsonNode elements = parse(authorizationDetails);
        for (int index = 0; index < elements.size(); index++) {
            checkSyntax(elements.get(index), index);
        }
        for (int index = 0; index < elements.size(); index++) {
            checkSemantics(elements.get(index), index, client);
        }
        return AuthorizationDetails.of(MAPPER.convertValue(elements, DETAILS));
    }

    private static JsonNode parse(String authorizationDetails) {
        JsonNode elements;
        try {
            elements = MAPPER.readTree(authorizationDetails);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(AUTHORIZATION_DETAILS + " is not valid JSON");
        }
        if (elements == null || !elements.isArray() || elements.isEmpty()) {
            throw new InvalidRequestException(AUTHORIZATION_DETAILS + " must be a non-empty JSON array");
        }
        return elements;
    }

    private static void checkSyntax(JsonNode element, int index) {
        if (!element.isObject()) {
            throw new InvalidRequestException(position(index) + " must be a JSON object");
        }
        JsonNode type = element.get("type");
        if (type == null || !type.isTextual() || type.asText().isEmpty()) {
            throw new InvalidRequestException(position(index) + " must have a non-empty string type");
        }
    }

    private void checkSemantics(JsonNode element, int index, Client client) {
        String type = element.get("type").asText();
        Optional<AuthorizationDetailType> authorizationDetailType = isEnabled(client, type)
                ? authorizationDetailTypeManager.findByType(type)
                : Optional.empty();
        if (authorizationDetailType.isEmpty()) {
            throw new InvalidAuthorizationDetailsException(describe(index, type) + " is not allowed for this client");
        }
        if (!matchesSchema(authorizationDetailType.get(), element)) {
            throw new InvalidAuthorizationDetailsException(describe(index, type) + " does not match its schema");
        }
    }

    /**
     * A schema that does not compile rejects every element.
     */
    private boolean matchesSchema(AuthorizationDetailType authorizationDetailType, JsonNode element) {
        String source = authorizationDetailType.getSchema();
        if (source == null || source.isBlank()) {
            return true;
        }
        Schema schema = schemas.compute(authorizationDetailType.getId(),
                (id, cached) -> cached != null && cached.source().equals(source) ? cached : compile(id, source)).schema();
        if (schema == null) {
            return false;
        }
        try {
            return schema.validate(element).isEmpty();
        } catch (Exception e) {
            log.error("Validation against the schema of authorization detail type {} failed", authorizationDetailType.getId(), e);
            return false;
        }
    }

    private static CompiledSchema compile(String id, String source) {
        try {
            return new CompiledSchema(source, SCHEMA_REGISTRY.getSchema(MAPPER.readTree(source)));
        } catch (Exception e) {
            log.error("Schema of authorization detail type {} cannot be used; its elements are rejected", id, e);
            return new CompiledSchema(source, null);
        }
    }

    private static boolean isEnabled(Client client, String type) {
        List<ApplicationAuthorizationDetailTypeSettings> enabled = client.getAuthorizationDetailTypes();
        return enabled != null && enabled.stream()
                .filter(Objects::nonNull)
                .anyMatch(settings -> type.equals(settings.getType()));
    }

    private static String position(int index) {
        return AUTHORIZATION_DETAILS + "[" + index + "]";
    }

    private static String describe(int index, String type) {
        if (type.length() <= TYPE_MAX_LENGTH && DESCRIBABLE_TYPE.matcher(type).matches()) {
            return position(index) + " of type '" + type + "'";
        }
        return position(index);
    }

    record CompiledSchema(String source, Schema schema) {
    }
}
