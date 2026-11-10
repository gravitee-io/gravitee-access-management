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
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    Field,
    FieldDescription,
    FieldLabel,
    Input,
} from '@gravitee/graphene-core';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../app/components/PageHeader';
import { useApplication } from './ApplicationContext';

const AGENT_TYPE_LABELS: Readonly<Record<string, string>> = {
    USER_EMBEDDED: 'User-embedded',
    AUTONOMOUS: 'Autonomous',
    HOSTED_DELEGATED: 'Hosted delegated',
};

export function ApplicationAgentPage() {
    const { application, applicationPath } = useApplication();
    const kind = application.kind ?? '';

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="Agent identity" description="How this agent acts: for a user, or on its own." />
            <Card>
                <CardHeader>
                    <CardTitle role="heading" aria-level={2}>
                        Agent settings
                    </CardTitle>
                    <CardDescription>
                        Grant types, token lifetimes, scopes, client authentication and JWKS are set on the{' '}
                        <Link to={`${applicationPath}/settings/oauth2`} className="font-medium text-foreground underline">
                            OAuth 2.0 / OIDC
                        </Link>{' '}
                        page.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <Field>
                        <FieldLabel htmlFor="application-agent-type">Agent type</FieldLabel>
                        <Input id="application-agent-type" className="w-72" readOnly value={AGENT_TYPE_LABELS[kind] ?? kind} />
                        <FieldDescription>Set when the agent was created. It cannot be changed.</FieldDescription>
                    </Field>
                </CardContent>
            </Card>
        </div>
    );
}
