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

import fetch from "cross-fetch";

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
			const link = dom("a").attr('href');
			console.log("URL extracted from email: " + link);
			return link;
		} else {
			throw 'Email content is missing';
		}
  }
}

class Content {
	data: string;
	contentType: string;
}

export async function getLastEmail() {
	const response = await fetch(process.env.FAKE_SMTP+'/api/email');
	const array = await response.json()
	const jsonEmail = array[0];

	const email = new Email();
	email.id = jsonEmail['id'];
	email.fromAddress = jsonEmail['fromAddress'];
	email.toAddress = jsonEmail['toAddress'];
	email.subject = jsonEmail['subject'];
	email.contents = jsonEmail['contents'].map(c => {
		const content = new Content();
		content.data = c['data'];
		content.contentType = c['contentType'];
		return content;
	});

	return email;
}

export async function clearEmails() {
	await fetch(process.env.FAKE_SMTP+'/api/email', {method: 'delete'});
}
