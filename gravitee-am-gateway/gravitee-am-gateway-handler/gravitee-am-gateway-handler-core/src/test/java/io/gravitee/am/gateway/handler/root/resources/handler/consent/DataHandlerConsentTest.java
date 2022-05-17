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

package io.gravitee.am.gateway.handler.root.resources.handler.consent;

import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_IP_LOCATION;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_USER_AGENT;
import static io.gravitee.common.http.MediaType.APPLICATION_JSON;
import static org.junit.Assert.*;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DataHandlerConsentTest {

    private SpyRoutingContext routingContext;
    private DataConsentHandler handler;

    @Before
    public void setUp() {
        handler = new DataConsentHandler();
        routingContext = new SpyRoutingContext();
    }

    @Test
    public void must_do_nothing_when_no_request_param() {
        handler.handle(routingContext);
        assertNull(routingContext.session().get(USER_CONSENT_IP_LOCATION));
        assertNull(routingContext.session().get(USER_CONSENT_USER_AGENT));
    }

    @Test
    public void must_have_user_ip_location_when_no_request_param() {
        routingContext.putParam(USER_CONSENT_IP_LOCATION, "on");
        handler.handle(routingContext);
        assertTrue(routingContext.session().get(USER_CONSENT_IP_LOCATION));
        assertNull(routingContext.session().get(USER_CONSENT_USER_AGENT));
    }

    @Test
    public void must_have_user_agent_when_no_request_param() {
        routingContext.putParam(USER_CONSENT_USER_AGENT, "on");
        handler.handle(routingContext);
        assertNull(routingContext.session().get(USER_CONSENT_IP_LOCATION));
        assertTrue(routingContext.session().get(USER_CONSENT_USER_AGENT));
    }

    @Test
    public void must_have_both_consent_true() {
        routingContext.putParam(USER_CONSENT_IP_LOCATION, "on");
        routingContext.putParam(USER_CONSENT_USER_AGENT, "on");
        handler.handle(routingContext);
        assertTrue(routingContext.session().get(USER_CONSENT_IP_LOCATION));
        assertTrue(routingContext.session().get(USER_CONSENT_USER_AGENT));
    }

    @Test
    public void must_have_both_consent_false() {
        routingContext.putParam(USER_CONSENT_IP_LOCATION, "somethingElse");
        routingContext.putParam(USER_CONSENT_USER_AGENT, "off");
        handler.handle(routingContext);
        assertFalse(routingContext.session().get(USER_CONSENT_IP_LOCATION));
        assertFalse(routingContext.session().get(USER_CONSENT_USER_AGENT));
    }

    @Test
    public void must_have_ip_location_true_and_user_agent_false() {
        routingContext.putParam(USER_CONSENT_IP_LOCATION, "on");
        routingContext.putParam(USER_CONSENT_USER_AGENT, "off");
        handler.handle(routingContext);
        assertTrue(routingContext.session().get(USER_CONSENT_IP_LOCATION));
        assertFalse(routingContext.session().get(USER_CONSENT_USER_AGENT));
    }

    @Test
    public void must_have_ip_location_false_and_user_agent_true() {
        routingContext.putParam(USER_CONSENT_IP_LOCATION, "off");
        routingContext.putParam(USER_CONSENT_USER_AGENT, "on");
        handler.handle(routingContext);
        assertFalse(routingContext.session().get(USER_CONSENT_IP_LOCATION));
        assertTrue(routingContext.session().get(USER_CONSENT_USER_AGENT));
    }

    @Test
    public void must_have_user_ip_location_when_no_request_param_body() {
        routingContext.request().headers().set("Content-type", APPLICATION_JSON);
        routingContext.setBody(new Buffer(new JsonObject(Map.of(USER_CONSENT_IP_LOCATION, "on")).toBuffer()));
        handler.handle(routingContext);
        assertTrue(routingContext.session().get(USER_CONSENT_IP_LOCATION));
        assertNull(routingContext.session().get(USER_CONSENT_USER_AGENT));
    }

    @Test
    public void must_have_user_agent_when_no_request_param_body() {
        routingContext.request().headers().set("Content-type", APPLICATION_JSON);
        routingContext.setBody(new Buffer(new JsonObject(Map.of(USER_CONSENT_USER_AGENT, "on")).toBuffer()));
        handler.handle(routingContext);
        assertNull(routingContext.session().get(USER_CONSENT_IP_LOCATION));
        assertTrue(routingContext.session().get(USER_CONSENT_USER_AGENT));
    }

    @Test
    public void must_have_both_consent_true_body() {
        routingContext.request().headers().set("Content-type", APPLICATION_JSON);
        routingContext.setBody(new Buffer(new JsonObject(Map.of(
                USER_CONSENT_IP_LOCATION, "on",
                USER_CONSENT_USER_AGENT, "on"
        )).toBuffer()));
        handler.handle(routingContext);
        assertTrue(routingContext.session().get(USER_CONSENT_IP_LOCATION));
        assertTrue(routingContext.session().get(USER_CONSENT_USER_AGENT));
    }

    @Test
    public void must_have_both_consent_false_body() {
        routingContext.request().headers().set("Content-type", APPLICATION_JSON);
        routingContext.setBody(new Buffer(new JsonObject(Map.of(
                USER_CONSENT_IP_LOCATION, "somethingElse",
                USER_CONSENT_USER_AGENT, "off"
        )).toBuffer()));
        handler.handle(routingContext);
        assertFalse(routingContext.session().get(USER_CONSENT_IP_LOCATION));
        assertFalse(routingContext.session().get(USER_CONSENT_USER_AGENT));
    }

    @Test
    public void must_have_ip_location_true_and_user_agent_false_body() {
        routingContext.request().headers().set("Content-type", APPLICATION_JSON);
        routingContext.setBody(new Buffer(new JsonObject(Map.of(
                USER_CONSENT_IP_LOCATION, "on",
                USER_CONSENT_USER_AGENT, "off"
        )).toBuffer()));
        handler.handle(routingContext);
        assertTrue(routingContext.session().get(USER_CONSENT_IP_LOCATION));
        assertFalse(routingContext.session().get(USER_CONSENT_USER_AGENT));
    }

    @Test
    public void must_have_ip_location_false_and_user_agent_true_body() {
        routingContext.request().headers().set("Content-type", APPLICATION_JSON);
        routingContext.setBody(new Buffer(new JsonObject(Map.of(
                USER_CONSENT_IP_LOCATION, "off",
                USER_CONSENT_USER_AGENT, "on"
        )).toBuffer()));
        handler.handle(routingContext);
        assertFalse(routingContext.session().get(USER_CONSENT_IP_LOCATION));
        assertTrue(routingContext.session().get(USER_CONSENT_USER_AGENT));
    }
}
