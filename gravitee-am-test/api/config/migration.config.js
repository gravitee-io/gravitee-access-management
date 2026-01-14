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
module.exports = {
    verbose: true,
    rootDir: '../..',
    setupFiles: ['./api/config/migration.setup.js'],
    moduleNameMapper: {
        '@management-apis/(.*)': '<rootDir>/api/management/apis/$1',
        '@management-commands/(.*)': '<rootDir>/api/commands/management/$1',
        '@gateway-commands/(.*)': '<rootDir>/api/commands/gateway/$1',
        '@utils-commands/(.*)': '<rootDir>/api/commands/utils/$1',
        '@utils/(.*)': '<rootDir>/api/utils/$1',
        '@api-fixtures/(.*)': '<rootDir>/api/fixtures/$1',
    },
};
