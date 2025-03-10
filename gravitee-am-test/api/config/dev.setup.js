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
process.env.AM_BASE_URL = 'http://localhost:4200';
process.env.AM_MANAGEMENT_URL = 'http://localhost:8093';
process.env.AM_MANAGEMENT_ENDPOINT = process.env.AM_MANAGEMENT_URL + '/management';
process.env.AM_GATEWAY_URL = 'http://localhost:8092';
process.env.AM_CIBA_NOTIFIER_URL = 'http://localhost:8080/ciba';
process.env.AM_DEF_ORG_ID = 'DEFAULT';
process.env.AM_DEF_ENV_ID = 'DEFAULT';
process.env.AM_ADMIN_USERNAME = 'admin';
process.env.AM_ADMIN_PASSWORD = 'adminadmin';
process.env.FAKE_SMTP = 'http://localhost:5080';
process.env.AM_GATEWAY_SYNC_GRACE_PERIOD = '5000';
// here is some Settings that allow Jest Tests execution on Relational DB
//process.env.GRAVITEE_REPOSITORIES_MANAGEMENT_TYPE="jdbc";
//process.env.GRAVITEE_REPOSITORIES_OAUTH2_JDBC_DATABASE= "postgres";
//process.env.GRAVITEE_REPOSITORIES_OAUTH2_JDBC_PASSWORD="xxxxx"
