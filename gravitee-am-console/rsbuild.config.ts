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
import { defineConfig } from '@rsbuild/core';
import { pluginReact } from '@rsbuild/plugin-react';

const managementApiUrl = process.env.AM_MAPI_URL ?? 'http://localhost:8093';

export default defineConfig({
    server: {
        port: 4201,
        // `changeOrigin: false` keeps the dev server host in the `Host` header, so the absolute URLs the Management API
        // builds for its login form and redirects point back at the dev server.
        proxy: {
            '/management': { target: managementApiUrl, changeOrigin: false },
        },
        historyApiFallback: true,
    },
    source: {
        entry: { index: './src/main.tsx' },
    },
    html: {
        template: './src/index.html',
    },
    output: {
        distPath: { root: 'dist' },
        assetPrefix: './',
        copy: [{ from: './public' }],
    },
    plugins: [pluginReact()],
});
