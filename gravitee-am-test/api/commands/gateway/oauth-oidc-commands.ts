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


import {expect} from "@jest/globals";

const supertest = require('supertest');
const cheerio = require('cheerio');

const URLS = {
    WELL_KNOWN_OPENID_CONFIG: "/oidc/.well-known/openid-configuration"
}

function setHeaders(request, headers: object) {
    if (headers) {
        for (const [k, v] of Object.entries(headers)) {
            request.set(k, v);
        }
    }
}

export const performPost = (baseUrl, uri = '', body = null, headers = null) => {
    const request = supertest(baseUrl).post(uri);
    setHeaders(request, headers);
    return body ? request.send(body) : request.send();
}

export const performFormPost = (baseUrl, uri = '', body, headers) => {
    const request = supertest(baseUrl).post(uri);
    setHeaders(request, headers);
    return request.field(body);
}

export const performGet = (baseUrl, uri = '', headers = null) => {
    const request = supertest(baseUrl).get(uri);
    setHeaders(request, headers);
    return request.send();
}

export const getWellKnownOpenIdConfiguration = (domainHrid) =>
    performGet(process.env.AM_GATEWAY_URL, `/${domainHrid}/${URLS.WELL_KNOWN_OPENID_CONFIG}`)


export const extractXsrfTokenAndActionResponse = async (response) => {
    const headers = response.headers['set-cookie'] ? {'Cookie': response.headers['set-cookie']} : {};
    const result = await performGet(response.headers['location'], '', headers).expect(200);
    const dom = cheerio.load(result.text);
    const xsrfToken = dom("[name=X-XSRF-TOKEN]").val();
    const action = dom("form").attr('action');

    expect(xsrfToken).toBeDefined();
    expect(action).toBeDefined();

    return {'headers': result.headers, 'token': xsrfToken, 'action': action};
}

export const extractXsrfToken = async (url, parameters) => {
    const result = await performGet(url, parameters).expect(200);
    const dom = cheerio.load(result.text);
    const xsrfToken = dom("[name=X-XSRF-TOKEN]").val();

    expect(xsrfToken).toBeDefined();
    return {'headers': result.headers, 'token': xsrfToken};
}

export const logoutUser = async (uri, postLogin: any) =>
    await performGet(uri, '', {'Cookie': postLogin.headers['set-cookie']}).expect(302);

