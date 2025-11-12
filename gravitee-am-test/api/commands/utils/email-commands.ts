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

import fetch from 'cross-fetch';

const cheerio = require('cheerio');

export class Email {
  id: number;
  fromAddress: string;
  toAddress: string;
  subject: string;
  contents: Array<Content>;

  public extractLink() {
    if (this.contents.length > 0) {
      const dom = cheerio.load(this.contents[0].data);
      return dom('a').attr('href');
    } else {
      throw new Error('Email content is missing');
    }
  }
}

class Content {
  data: string;
  contentType: string;
}

export async function getLastEmail(delay = 1000, toAddress?: string) {
  await new Promise((r) => setTimeout(r, delay));
  const response = await fetch(process.env.FAKE_SMTP + '/api/email');
  const array = await response.json();
  if (!array || array.length === 0) {
    throw new Error('No emails available');
  }

  // Filter by recipient address if provided
  let jsonEmail;
  if (toAddress) {
    jsonEmail = array.find((email: any) => email['toAddress'] === toAddress);
    if (!jsonEmail) {
      throw new Error(`No email found for recipient: ${toAddress}`);
    }
  } else {
    // If no filter, return the first (most recent) email
    jsonEmail = array[0];
  }

  const email = new Email();
  email.id = jsonEmail['id'];
  email.fromAddress = jsonEmail['fromAddress'];
  email.toAddress = jsonEmail['toAddress'];
  email.subject = jsonEmail['subject'];
  email.contents = jsonEmail['contents'].map((c) => {
    const content = new Content();
    content.data = c['data'];
    content.contentType = c['contentType'];
    return content;
  });

  return email;
}

export async function clearEmails(toAddress?: string) {
  if (!toAddress) {
    // Clear all emails (backward compatible)
    await fetch(process.env.FAKE_SMTP + '/api/email', { method: 'delete' });
    return;
  }

  // Clear emails for a specific recipient only
  // Fetch all emails, filter by recipient, and delete each one
  const response = await fetch(process.env.FAKE_SMTP + '/api/email');
  const array = await response.json();
  if (!array || array.length === 0) {
    return;
  }

  // Filter emails by recipient address
  const emailsToDelete = array.filter((email: any) => email['toAddress'] === toAddress);
  if (emailsToDelete.length === 0) {
    return;
  }

  // Delete each email by ID (FAKE_SMTP typically supports DELETE /api/email/{id})
  for (const email of emailsToDelete) {
    try {
      await fetch(`${process.env.FAKE_SMTP}/api/email/${email['id']}`, { method: 'delete' });
    } catch (error: unknown) {
      // If individual deletion fails, log and continue
      // This is a fallback for FAKE_SMTP versions that don't support DELETE by ID
      // In that case, we rely on filtering in getLastEmail() to avoid interference
      const errorMessage = error instanceof Error ? error.message : String(error);
      console.debug(`Failed to delete email ${email['id']} for ${toAddress}, will rely on filtering: ${errorMessage}`);
    }
  }
}

export async function hasEmail(delay = 1000, toAddress?: string) {
  await new Promise((r) => setTimeout(r, delay));
  const response = await fetch(process.env.FAKE_SMTP + '/api/email');
  const array = await response.json();
  if (!array || array.length === 0) {
    return false;
  }

  // Filter by recipient address if provided
  if (toAddress) {
    return array.some((email: any) => email['toAddress'] === toAddress);
  }

  return array.length > 0;
}
