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
db.getCollection("roles").updateMany(
    {
        name: {$in: ["ORGANIZATION_PRIMARY_ONWER", "ORGANIZATION_OWNER", "ENVIRONMENT_PRIMARY_OWNER", "ENVIRONMENT_OWNER", "DOMAIN_PRIMARY_OWNER", "DOMAIN_OWNER"]},
        "permissionAcls.DOMAIN_USER_DEVICE": {$exists: false},
    },
    {
        $set: {"permissionAcls.DOMAIN_USER_DEVICE": ["DELETE", "LIST"]},
    }
);
