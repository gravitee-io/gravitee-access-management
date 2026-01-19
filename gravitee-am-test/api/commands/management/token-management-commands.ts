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
import request from 'supertest';
const btoa = require('btoa');

export const requestAdminAccessToken = () => requestAccessToken(process.env.AM_ADMIN_USERNAME, process.env.AM_ADMIN_PASSWORD);

export const requestAccessToken = (username: string, password: string) => {
  return request(process.env.AM_MANAGEMENT_URL)
    .post('/management/auth/token')
    .set('Authorization', 'Basic ' + btoa(`${username}:${password}`))
    .send({ grant_type: 'password', username: username, password: password })
    .expect(200)
    .then((res) => res.body.access_token);
};
