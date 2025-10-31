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
export interface JWT {
  header: object;
  payload: object;
  signature: string;
}

export function parseJwt(jwt: string): JWT {
  const split = jwt.split('.');
  return {
    header: JSON.parse(Buffer.from(split[0], 'base64').toString('binary')),
    payload: JSON.parse(Buffer.from(split[1], 'base64').toString('binary')),
    signature: split[2],
  };
}
