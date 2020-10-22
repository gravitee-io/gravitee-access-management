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
// Migrate forms content
db.getCollection("forms")
    .find({})
    .forEach(function (form) {
        let content = form.content;
        content = content.replace("@{login}", "${action}");
        content = content.replace("@{forgotPassword}", "${action}");
        content = content.replace("@{confirmRegistration}", "${action}");
        content = content.replace("@{resetPassword}", "${action}");

        content = content.replace(
            "@{login(client_id=${param.client_id})}",
            "${loginAction}"
        );
        content = content.replace(
            "@{forgotPassword(client_id=${param.client_id})}",
            "${forgotPasswordAction}"
        );
        content = content.replace(
            "@{register(client_id=${param.client_id})}",
            "${registerAction}"
        );
        content = content.replace(
            "@{webauthn/login(client_id=${param.client_id})}",
            "${passwordlessAction}"
        );
        content = content.replace("@{consent}", "${action}");
        content = content.replace("@{register}", "${action}");
        content = content.replace(
            "@{../login(client_id=${param.client_id})}",
            "${loginAction}"
        );
        content = content.replace(
            "@{register(client_id=${param.client_id}, prompt='none')}",
            "${skipAction}"
        );

        db.getCollection("forms").update(
            {_id: form._id},
            {$set: {content: content}}
        );
    });
