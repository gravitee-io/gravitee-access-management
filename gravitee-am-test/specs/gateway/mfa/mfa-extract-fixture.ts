import { val } from 'cheerio/lib/api/attributes';
import { expect } from '@jest/globals';

const cheerio = require('cheerio');

export async function extractDomValue(response, selector): Promise<string> {
  const dom = cheerio.load(response.text);
  const value = dom(selector).val();
  expect(value).toBeDefined();
  return value;
}

export async function extractDomAttr(response, selector, attr): Promise<string> {
  const dom = cheerio.load(response.text);
  const value = dom(selector).attr(attr);
  expect(value).toBeDefined();
  return value;
}
