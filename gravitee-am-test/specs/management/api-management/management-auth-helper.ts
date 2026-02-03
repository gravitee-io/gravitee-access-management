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

import { expect } from '@jest/globals';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
const cheerio = require('cheerio');

/**
 * Build Cookie header value from response set-cookie (array or string).
 * Supertest expects Cookie to be a string; never pass undefined.
 */
export function cookieHeaderFromSetCookie(setCookie: string | string[] | undefined): string | undefined {
  if (setCookie == null) {
    return undefined;
  }

  const arr = Array.isArray(setCookie) ? setCookie : [setCookie];
  if (arr.length === 0) {
    return undefined;
  }
  return arr.map((c) => c.split(';')[0].trim()).join('; ');
}

/**
 * Merge two Cookie header values (new overwrites same-name cookies in existing).
 * Used to maintain a per-origin cookie jar when following redirects across origins.
 */
export function mergeCookieStrings(existing: string | undefined, newStr: string | undefined): string {
  const map: Record<string, string> = {};
  const parse = (s: string) => {
    s.split(';').forEach((part) => {
      const trimmed = part.trim();
      const eq = trimmed.indexOf('=');
      if (eq > 0) map[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim();
    });
  };
  if (existing) parse(existing);
  if (newStr) parse(newStr);
  return Object.entries(map)
    .map(([k, v]) => `${k}=${v}`)
    .join('; ');
}

/**
 * Extract CSRF token from management login form HTML.
 * Management console may use _csrf (hidden input) or X-XSRF-TOKEN.
 */
export function extractCsrfFromManagementLoginHtml(html: string): string {
  const $ = cheerio.load(html);
  const csrf = ($('[name="_csrf"]').val() as string) || ($('[name="X-XSRF-TOKEN"]').val() as string);
  expect(csrf).toBeDefined();
  
  return csrf;
}

/**
 * Check if the login form HTML contains a social provider link.
 */
export function hasSocialProviderLink(html: string): boolean {
  const $ = cheerio.load(html);
  const href =
    $('.button.social.btn-oauth2-generic-am-idp').attr('href') ||
    $('[class*="social"][class*="oauth2-generic-am-idp"]').attr('href') ||
    $('a[href*="oauth2"], a[href*="social"]').first().attr('href') ||
    $('a[href*="/oauth/authorize"], a[href*="client_id"]').first().attr('href') ||
    $('a.btn[href]').filter((_, el) => $(el).attr('href')?.includes('authorize') || $(el).attr('href')?.includes('login')).first().attr('href');
  return !!href;
}

/**
 * Extract social provider button href from management login form (class contains social and oauth2-generic-am-idp).
 * Replaces internal_gateway_url with gateway_url in the href.
 */
export function extractSocialUrlFromManagementLoginHtml(
  html: string,
  internalGatewayUrl: string,
  gatewayUrl: string,
): string {
  const $ = cheerio.load(html);
  let href =
    $('.button.social.btn-oauth2-generic-am-idp').attr('href') ||
    $('[class*="social"][class*="oauth2-generic-am-idp"]').attr('href') ||
    $('a[href*="oauth2"], a[href*="social"]').first().attr('href') ||
    $('a[href*="/oauth/authorize"], a[href*="client_id"]').first().attr('href') ||
    $('a.btn[href]').filter((_, el) => $(el).attr('href')?.includes('authorize') || $(el).attr('href')?.includes('login')).first().attr('href');
  if (!href) {
    throw new Error('Could not find social provider link in login form');
  }
  return href.replace(internalGatewayUrl, gatewayUrl);
}

/**
 * Extract X-XSRF-TOKEN and form action from social domain login form HTML.
 */
export function extractXsrfAndActionFromSocialLoginHtml(html: string): { xsrf: string; action: string } {
  const $ = cheerio.load(html);
  const xsrf = ($('[name="X-XSRF-TOKEN"]').val() as string) || ($('[name="_csrf"]').val() as string);
  const action = $('form').attr('action');

  if (!xsrf || !action) {
    throw new Error('Could not find XSRF token or form action in social login form');
  }
  return { xsrf, action };
}

/**
 * Parse a Location header (absolute URL or path) into origin and path+query.
 * Relative locations are resolved against baseOrigin.
 */
export function parseLocation(
  location: string,
  baseOrigin: string,
): { origin: string; pathAndSearch: string } {
  if (!location) {
    throw new Error('Empty location');
  }
  if (location.startsWith('http://') || location.startsWith('https://')) {
    const u = new URL(location);
    return { origin: u.origin, pathAndSearch: u.pathname + u.search };
  }
  const base = baseOrigin.endsWith('/') ? baseOrigin : baseOrigin + '/';
  const u = new URL(location, base);
  return { origin: u.origin, pathAndSearch: u.pathname + u.search };
}

/**
 * Start management auth flow: GET authorize, follow redirect to login page, return login form response and cookie.
 * Fails if location or Set-Cookie is missing (deterministic true path).
 */
export async function getLoginForm(managementUrl: string, redirectUri: string) {
  const authorizePath = `/management/auth/authorize?redirect_uri=${encodeURIComponent(redirectUri)}`;
  const initiateRes = await performGet(managementUrl, authorizePath);
  const location = initiateRes.headers.location;
  expect(location).toBeDefined();

  const { origin, pathAndSearch } = parseLocation(location!, managementUrl);
  const cookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
  expect(cookie).toBeDefined();
  
  const loginFormRes = await performGet(origin, pathAndSearch, { Cookie: cookie! });
  return { loginFormRes, cookie: cookie! };
}
