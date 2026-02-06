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
process.env.AM_MANAGEMENT_URL = 'http://localhost:8093';
process.env.AM_MANAGEMENT_ENDPOINT = process.env.AM_MANAGEMENT_URL + '/management';
process.env.AM_GATEWAY_URL = 'http://localhost:8092';
process.env.AM_DOMAIN_DATA_PLANE_ID = 'default';
process.env.AM_INTERNAL_GATEWAY_URL = 'http://localhost:8092';
process.env.AM_MONGODB_URI = 'mongodb://localhost:27017';
process.env.AM_POSTGRES_HOST = 'localhost';
process.env.AM_GATEWAY_NODE_MONITORING_URL = 'http://localhost:18092/_node';
process.env.AM_CIBA_NOTIFIER_URL = 'http://localhost:8080/ciba';
process.env.AM_INTERNAL_CIBA_NOTIFIER_URL = 'http://localhost:8080/ciba';
process.env.AM_OPENFGA_URL = 'http://localhost:8090';
process.env.AM_INTERNAL_OPENFGA_URL = 'http://localhost:8090';
process.env.AM_DEF_ORG_ID = 'DEFAULT';
process.env.AM_DEF_ENV_ID = 'DEFAULT';
process.env.AM_ADMIN_USERNAME = 'admin';
process.env.AM_ADMIN_PASSWORD = 'adminadmin';
process.env.FAKE_SMTP = 'http://localhost:5080';
process.env.INTERNAL_FAKE_SMTP_HOST = 'smtp';
process.env.INTERNAL_FAKE_SMTP_PORT = '5025';
process.env.AM_GATEWAY_SYNC_GRACE_PERIOD = '5000';
