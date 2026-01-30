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
const cheerio = require('cheerio');

/**
 * Build Cookie header value from response set-cookie (array or string).
 * Supertest expects Cookie to be a string; never pass undefined.
 */
export function cookieHeaderFromSetCookie(setCookie: string | string[] | undefined): string | undefined {
  if (setCookie == null) return undefined;
  const arr = Array.isArray(setCookie) ? setCookie : [setCookie];
  if (arr.length === 0) return undefined;
  return arr.map((c) => c.split(';')[0].trim()).join('; ');
}

/**
 * Extract CSRF token from management login form HTML.
 * Management console may use _csrf (hidden input) or X-XSRF-TOKEN.
 */
export function extractCsrfFromManagementLoginHtml(html: string): string {
  const $ = cheerio.load(html);
  const csrf =
    ($('[name="_csrf"]').val() as string) || ($('[name="X-XSRF-TOKEN"]').val() as string);
  if (!csrf) {
    throw new Error('Could not find _csrf or X-XSRF-TOKEN in login form HTML');
  }
  return csrf;
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
  if (!href) throw new Error('Could not find social provider link in login form');
  return href.replace(internalGatewayUrl, gatewayUrl);
}

/**
 * Extract X-XSRF-TOKEN and form action from social domain login form HTML.
 */
export function extractXsrfAndActionFromSocialLoginHtml(html: string): { xsrf: string; action: string } {
  const $ = cheerio.load(html);
  const xsrf = ($('[name="X-XSRF-TOKEN"]').val() as string) || ($('[name="_csrf"]').val() as string);
  const action = $('form').attr('action');
  if (!xsrf || !action) throw new Error('Could not find XSRF token or form action in social login form');
  return { xsrf, action };
}
