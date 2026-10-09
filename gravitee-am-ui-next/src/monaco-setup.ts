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
import type { Environment } from 'monaco-editor';

// Monaco runs its language services in web workers and asks the host for them. Without this it falls back to the main
// thread and logs "Unexpected usage". gamma-console does the same in its own `monaco-setup.ts`; federated modules inherit
// it from the host, a standalone console must declare it. The bundler emits each worker as its own chunk. AM edits
// HTML, JSON and CSS only, so the TypeScript worker, which also warns at build time, is left out.
const monacoEnvironment: Environment = {
    getWorker(_workerId: string, label: string): Worker {
        if (label === 'json') {
            return new Worker(new URL('monaco-editor/esm/vs/language/json/json.worker.js', import.meta.url));
        }
        if (label === 'css' || label === 'scss' || label === 'less') {
            return new Worker(new URL('monaco-editor/esm/vs/language/css/css.worker.js', import.meta.url));
        }
        if (label === 'html' || label === 'handlebars' || label === 'razor') {
            return new Worker(new URL('monaco-editor/esm/vs/language/html/html.worker.js', import.meta.url));
        }
        return new Worker(new URL('monaco-editor/esm/vs/editor/editor.worker.js', import.meta.url));
    },
};

window.MonacoEnvironment = monacoEnvironment;
