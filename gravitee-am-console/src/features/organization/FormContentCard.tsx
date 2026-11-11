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
    Tabs,
    TabsContent,
    TabsList,
    TabsTrigger,
} from '@gravitee/graphene-core';
import { CodeEditor } from '@gravitee/graphene-core/code-editor';
import { useState } from 'react';

interface FormContentCardProps {
    readonly enabled: boolean;
    readonly readOnly: boolean;
    readonly content: string;
    readonly onChange: (content: string) => void;
}

export function FormContentCard({ enabled, readOnly, content, onChange }: FormContentCardProps) {
    const [tab, setTab] = useState('html');
    return (
        <Card>
            <CardHeader>
                <CardTitle className="text-base">Content</CardTitle>
                <CardDescription>
                    {enabled ? 'The HTML served instead of the default.' : 'Enable the custom form to edit its HTML.'}
                </CardDescription>
            </CardHeader>
            <CardContent>
                <Tabs value={tab} onValueChange={setTab}>
                    <TabsList>
                        <TabsTrigger value="html">Html</TabsTrigger>
                        <TabsTrigger value="preview" disabled={readOnly}>
                            Preview
                        </TabsTrigger>
                    </TabsList>
                    <TabsContent value="html" className="pt-3">
                        <CodeEditor
                            language="html"
                            value={content}
                            readOnly={readOnly}
                            height={480}
                            onChange={value => onChange(value ?? '')}
                        />
                    </TabsContent>
                    <TabsContent value="preview" className="pt-3">
                        {/* No script runs in the preview, as the content is user-authored HTML. */}
                        <iframe
                            title="Preview"
                            sandbox=""
                            srcDoc={content}
                            className="h-[560px] w-full rounded-md border border-border bg-background"
                        />
                    </TabsContent>
                </Tabs>
            </CardContent>
        </Card>
    );
}
