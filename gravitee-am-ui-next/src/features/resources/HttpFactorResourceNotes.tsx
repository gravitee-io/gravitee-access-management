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
import { Alert, AlertDescription, AlertTitle } from '@gravitee/graphene-core';

/** The Angular page description for the HTTP Factor resource, on the creation and detail pages. */
export function HttpFactorResourceNotes() {
    return (
        <Alert>
            <AlertTitle>HTTP Factor Resource</AlertTitle>
            <AlertDescription className="space-y-2">
                <p>
                    The HTTP Factor resource facilitates HTTP requests to send and verify a generated, one time code. It supports Gravitee
                    Expression Language (EL) to configure the resource properties.
                </p>
                <p>
                    The response can be accessed via EL using xpath, e.g.: <code>{`{#xpath(#response.content, ".//code") == '1234'}`}</code>{' '}
                    where code is a property of the XML response.
                </p>
                <p>
                    The user pin or verification code is available in the <code>{`{#context.attributes['code']}`}</code> EL property after
                    the user has submitted the code for verification.
                </p>
            </AlertDescription>
        </Alert>
    );
}
