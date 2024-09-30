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

const cheerio = require('cheerio');

export function extractDomValue(response, selector): string {
  const dom = cheerio.load(response.text);
  const value = dom(selector).val();
  expect(value).toBeDefined();
  return value;
}

export function extractDomAttr(response, selector, attr): string {
  const dom = cheerio.load(response.text);
  const value = dom(selector).attr(attr);
  expect(value).toBeDefined();
  return value;
}

export function extractCookieSessionValues(cookie): any {
  const jwt = cookie[0]
    .split('; ')
    .filter((str) => str.includes('GRAVITEE_IO_AM_SESSION'))[0]
    .split('=')[1]
    .split('.')[1];
  const decoded = Buffer.from(jwt, 'base64').toString('binary');
  return JSON.parse(decoded);
}
