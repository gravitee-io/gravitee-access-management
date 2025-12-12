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

import { createIdp } from '@management-commands/idp-management-commands';

export const createMongoIdp = async (domainId, accessToken) => {
  console.log('creating mongodb  idp');
  return await createIdp(domainId, accessToken, {
    external: false,
    type: 'mongo-am-idp',
    domainWhitelist: [],
    configuration: JSON.stringify({
      uri: 'mongodb://mongodb:27017',
      host: 'localhost',
      port: 27017,
      enableCredentials: false,
      databaseCredentials: 'gravitee-am',
      database: 'gravitee-am',
      usersCollection: 'idp-test-users',
      findUserByUsernameQuery: '{$or: [{username: ?}, {contract: ?}]}',
      findUserByEmailQuery: '{email: ?}',
      usernameField: 'username',
      passwordField: 'password',
      passwordEncoder: 'None',
      useDedicatedSalt: false,
      passwordSaltLength: 32,
    }),
    name: 'another-idp',
  });
};

export const createJdbcIdp = async (domainId, accessToken) => {
  console.log('creating jdbc idp');
  const password = 'postgres';
  const database = 'postgres';

  return await createIdp(domainId, accessToken, {
    external: false,
    type: 'jdbc-am-idp',
    domainWhitelist: [],
    configuration: `{\"host\":\"postgres\",\"port\":5432,\"protocol\":\"postgresql\",\"database\":\"${database}\",\"usersTable\":\"test_users\",\"user\":\"postgres\",\"password\":\"${password}\",\"autoProvisioning\":\"true\",\"selectUserByUsernameQuery\":\"SELECT * FROM test_users WHERE username = %s\",\"selectUserByMultipleFieldsQuery\":\"SELECT * FROM test_users WHERE username = %s or email = %s\",\"selectUserByEmailQuery\":\"SELECT * FROM test_users WHERE email = %s\",\"identifierAttribute\":\"id\",\"usernameAttribute\":\"username\",\"emailAttribute\":\"email\",\"passwordAttribute\":\"password\",\"passwordEncoder\":\"None\",\"useDedicatedSalt\":false,\"passwordSaltLength\":32}`,
    name: 'other-jdbc-idp',
  });
};
