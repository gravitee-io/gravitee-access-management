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
package io.gravitee.am.gateway.handler.oidc.request;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClaimsRequestResolverTest {

    private ClaimsRequestResolver claimsRequestResolver = new ClaimsRequestResolver();

    @Test
    public void shouldResolveClaimsRequest_userInfo() throws ClaimsRequestSyntaxException {
        String claims = "{\"userinfo\": {\"name\": {\"essential\": true}, \"family_name\": null}}";
        ClaimsRequest claimsRequest = claimsRequestResolver.resolve(claims);
        Assert.assertNull(claimsRequest.getIdTokenClaims());
        Assert.assertNotNull(claimsRequest.getUserInfoClaims());
        Assert.assertTrue(claimsRequest.getUserInfoClaims().size() == 2);
        Assert.assertTrue(claimsRequest.getUserInfoClaims().containsKey("name"));
        Assert.assertTrue(claimsRequest.getUserInfoClaims().containsKey("family_name"));
    }

    @Test
    public void shouldResolveClaimsRequest_userInfo_unrecognized_value() throws ClaimsRequestSyntaxException {
        String claims = "{\"userinfo\": {\"name\": {\"essential\": true}, \"family_name\": \"unrecognized\"}}";
        ClaimsRequest claimsRequest = claimsRequestResolver.resolve(claims);
        Assert.assertNull(claimsRequest.getIdTokenClaims());
        Assert.assertNotNull(claimsRequest.getUserInfoClaims());
        Assert.assertTrue(claimsRequest.getUserInfoClaims().size() == 1);
        Assert.assertTrue(claimsRequest.getUserInfoClaims().containsKey("name"));
        Assert.assertFalse(claimsRequest.getUserInfoClaims().containsKey("family_name"));
    }

    @Test
    public void shouldResolveClaimsRequest_idToken() throws ClaimsRequestSyntaxException {
        String claims = "{\"id_token\": {\"name\": {\"essential\": true}, \"family_name\": null}}";
        ClaimsRequest claimsRequest = claimsRequestResolver.resolve(claims);
        Assert.assertNull(claimsRequest.getUserInfoClaims());
        Assert.assertNotNull(claimsRequest.getIdTokenClaims());
        Assert.assertTrue(claimsRequest.getIdTokenClaims().size() == 2);
        Assert.assertTrue(claimsRequest.getIdTokenClaims().containsKey("name"));
        Assert.assertTrue(claimsRequest.getIdTokenClaims().containsKey("family_name"));
    }

    @Test
    public void shouldResolveClaimsRequest_idToken_unrecognized_value() throws ClaimsRequestSyntaxException {
        String claims = "{\"id_token\": {\"name\": {\"essential\": true}, \"family_name\": \"unrecognized\"}}";
        ClaimsRequest claimsRequest = claimsRequestResolver.resolve(claims);
        Assert.assertNull(claimsRequest.getUserInfoClaims());
        Assert.assertNotNull(claimsRequest.getIdTokenClaims());
        Assert.assertTrue(claimsRequest.getIdTokenClaims().size() == 1);
        Assert.assertTrue(claimsRequest.getIdTokenClaims().containsKey("name"));
        Assert.assertFalse(claimsRequest.getIdTokenClaims().containsKey("family_name"));
    }

    @Test
    public void shouldResolveClaimsRequest() throws ClaimsRequestSyntaxException {
        String claims = "{ \"userinfo\": {\"name\": {\"essential\": true}, \"family_name\": null}, " +
                "\"id_token\": {\"name\": {\"essential\": true}, \"family_name\": null}}";
        ClaimsRequest claimsRequest = claimsRequestResolver.resolve(claims);
        Assert.assertNotNull(claimsRequest.getUserInfoClaims());
        Assert.assertNotNull(claimsRequest.getIdTokenClaims());
        Assert.assertTrue(claimsRequest.getUserInfoClaims().size() == 2);
        Assert.assertTrue(claimsRequest.getUserInfoClaims().containsKey("name"));
        Assert.assertTrue(claimsRequest.getUserInfoClaims().containsKey("family_name"));
        Assert.assertTrue(claimsRequest.getIdTokenClaims().size() == 2);
        Assert.assertTrue(claimsRequest.getIdTokenClaims().containsKey("name"));
        Assert.assertTrue(claimsRequest.getIdTokenClaims().containsKey("family_name"));
    }

    @Test(expected = ClaimsRequestSyntaxException.class)
    public void shouldNotResolveClaimsRequest() throws ClaimsRequestSyntaxException {
        String claims = "no_json";
        claimsRequestResolver.resolve(claims);
    }
}
