/**
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
package io.gravitee.sample.ciba.notifier.http;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface Constants {

    String TOPIC_NOTIFICATION_REQUEST = "notify-request";

    String TRANSACTION_ID = "tid";
    String STATE = "state";
    String PARAM_SUBJECT = "subject";
    String PARAM_SCOPE = "scope";
    String PARAM_EXPIRE = "expire";
    String PARAM_MESSAGE = "message";

    String BEARER = "Bearer ";

    String ACTION_REJECT = "reject";
    String ACTION_VALIDATE = "validate";
    String ACTION_SIGN_IN = "signIn";
    String ACTION = "action";

    String CALLBACK_VALIDATE = "validated";
}
