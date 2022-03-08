/*
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
export const domainOidcCibaConfig = (notifierId) => {
    return { oidc: {
        clientRegistrationSettings: {
            allowLocalhostRedirectUri: true,
            allowHttpSchemeRedirectUri: true,
            allowWildCardRedirectUri: true,
            isDynamicClientRegistrationEnabled: true,
            isOpenDynamicClientRegistrationEnabled: true
        },
        cibaSettings: {
            authReqExpiry: 600,
            tokenReqInterval: 1,
            bindingMessageLength: 256,
            deviceNotifiers: [{id:notifierId}],
            enabled: true
        }
    }};
};

export const privateJwk = {
    "p": "-JXCCIZsXZ5aNZNtwxMRO67QSycjCxuw6VFKUQL8Iavrnk08K7HWVmncqusAdJV9NZUlqzW8eVxjrbGoTn0WIX-sqwlf3-JJS6KY4vHXh_wxLfmyoKXDDbS3xCa0OOwSA1I9VcFHW73ETTCGjVlOdrtwoMLKhGiZLJJxkhGKiVM",
    "kty": "RSA",
    "q": "wCAgYZjSEVHJghRs29SuhgWHRUY6-R8NHVrxpw937DV1u11ERq6c41ffsGHfiQjyN_I9L_sy2UkmosCWLy_f6cyZHe_ZQseBob2tFVLgUmNIf1fwvLSOpkvmE95yZxAJCmEXkG4gvxMR8I2kVhCHxQIUYy_Gfhi0rVw6hQaKHs8",
    "d": "lNBn1je9USaIGDbTDC_xRBISfPajVfykVLNEfUJ5CLTFMyUfv59wecyga2Wlj7pCkn8kgQcHfyr_jGSM8lTD4pDuRMf9KD8zTywHBi2WO6DzbOxMwi3uUvam6aoMTqTrFLidOW05CWTsvbe9Odl9Mcz4zzC7ije-1Lx2iEAtilifluNwBL99UDFZg-3J6amXWZU42fwvmEVDx_aJn2Ehcuf1MhECGyBM4QMRasbCzzYFEaOARhsfOnyJvdTQyfeKIfa7FbSC7R2VqlVesOtxPzL9oc8qcdxE1wZ7l2Ux4J5stGwnpVrfB9dBqWJefdkq4Kv-bMOmvJgDd0NF8yObLQ",
    "e": "AQAB",
    "use": "sig",
    "kid": "123",
    "qi": "jPewLuOPDnZYvli0baqx1dEhVHjThF2OPRMTHP3v1zbWem4lWYkGUoz2qeXnBVejCdFsod9V9M6lTAsBwBj99dyS-y7n1rDuRWd__16KaXMA14OaB7lf-eE6gFah-PN5gVAKo71k1u-J_WOPkfBT-kcLbHHzQFqNPyPiy4HV_Rk",
    "dp": "5DVv1S267FNEk6zN9mlZx8Xb2TKLvFXmmruTEz4_Q5Y2D7TuCVsQ33H-MDbfyye1s-xBkaUaavvDUqEnVy8EkypH1RkdGEcAbNxPqQDGkkOWzpNORqcGo12F2yCBEUS_4KauQjzXCsTzIr3quHcFToETi7JoAxiXjlC-zI8n9Js",
    "dq": "SPOp-AUkNulcX6VL1IlEn6U3wQky2Wd9_liLC8lm2u1NwBBhHYmuDvFOAdaYH5ujBbVYoIB8xV7uabxBCrfeCRPkTCbH04CX64dvUnp-rSn_3ELTKYRR6jlFquO7gwDmvecyIGiAzKz8EeBmtztdomPww9zfPQA6kt1DZ0Gdbqc",
    "n": "uo-DsCNGkKqJ9jDgzmS3-GCFvezuvb0b0Qux58Y_DzbIPM_6xg9J9J1weCSiWg4GxXcBtbrd6bsc1dyj9yKRpJ3I_t68BCeGvhaQ-LYcfyQ36ckw-ibG3wYHECFoOd5sxSvDnswCy1er5vgMCOf-wzHjfZJAkQudq7gl0-45D_T_syRqbTOZ_GZiNF1mJD0493VGvkLFwsKrLbPUpZeOev74X2rMS8RnLsvglzoS3ycvFKwKk9EcK6wxV6a59h-vCUQy28BJIJYd9W5SNT6M655ZikpacbIsIcaTO0L3FO4UxWGaL7Z6Y5EboO7B8Ev4amrCGzY7WH3Jyc0vY9rEHQ"
};

export const publicJwk = {
    "kty": "RSA",
    "e": "AQAB",
    "use": "sig",
    "kid": "123",
    "n": "uo-DsCNGkKqJ9jDgzmS3-GCFvezuvb0b0Qux58Y_DzbIPM_6xg9J9J1weCSiWg4GxXcBtbrd6bsc1dyj9yKRpJ3I_t68BCeGvhaQ-LYcfyQ36ckw-ibG3wYHECFoOd5sxSvDnswCy1er5vgMCOf-wzHjfZJAkQudq7gl0-45D_T_syRqbTOZ_GZiNF1mJD0493VGvkLFwsKrLbPUpZeOev74X2rMS8RnLsvglzoS3ycvFKwKk9EcK6wxV6a59h-vCUQy28BJIJYd9W5SNT6M655ZikpacbIsIcaTO0L3FO4UxWGaL7Z6Y5EboO7B8Ev4amrCGzY7WH3Jyc0vY9rEHQ"
};

export const oidcApplication = {
    redirect_uris: ["https://callback"],
    client_name: "CIBA App",
    application_type: "web",
    grant_types: ["authorization_code", "refresh_token", "urn:openid:params:grant-type:ciba"],
    response_types: [
        "code",
        "code id_token token",
        "code id_token",
        "code token"
    ],
    scope: "openid profile",
    default_acr_values: ["urn:mace:incommon:iap:silver"],
    token_endpoint_auth_method: "client_secret_basic",
    backchannel_token_delivery_mode: "poll",
    backchannel_user_code_parameter: true,
    backchannel_authentication_request_signing_alg: "RS256",
    client_id: "cibatest",
    client_secret: "cibatest",
    jwks: {keys: [publicJwk]},
    request_object_signing_alg: "RS256"
};
