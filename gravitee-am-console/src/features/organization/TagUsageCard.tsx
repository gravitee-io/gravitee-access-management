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
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CopyableText } from '@gravitee/graphene-core';

export function TagUsageCard({ tagId }: { readonly tagId: string }) {
    return (
        <Card>
            <CardHeader>
                <CardTitle className="text-base">Manage sharding tag</CardTitle>
                <CardDescription>
                    A sharding-tag determines how security domain will be deployed across multiple AM Gateway. To make the association
                    between security domains and Gateway instances, you must copy/paste the shard tag’s ID to the gravitee.yml file of the
                    gateway instance.
                </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
                <CopyableText className="max-w-full" value={tagId} />
                <pre className="rounded-md bg-muted p-3 font-mono text-sm">
                    {'# Sharding tags configuration\n# tags: public,private,!international'}
                </pre>
            </CardContent>
        </Card>
    );
}
