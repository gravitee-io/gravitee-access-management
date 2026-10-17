import { defineConfig } from '@rsbuild/core';
import { pluginReact } from '@rsbuild/plugin-react';

// `yarn serve` proxies `/management` to a running AM Management API so the session cookie and the redirects of the
// login chain stay on the dev server origin. Override the target with `AM_MAPI_URL`.
const managementApiUrl = process.env.AM_MAPI_URL ?? 'http://localhost:8093';
// `/gamma` goes to the APIM rest-api that lists the Gamma modules. Override with `GAMMA_API_URL`.
const gammaApiUrl = process.env.GAMMA_API_URL ?? 'http://localhost:8083';

export default defineConfig({
    server: {
        port: 4201,
        // `changeOrigin: false` keeps the dev server host in the `Host` header, so the absolute URLs the Management API
        // builds for its login form and redirects point back at the dev server.
        proxy: {
            '/management': { target: managementApiUrl, changeOrigin: false },
            '/gamma': { target: gammaApiUrl, changeOrigin: true },
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
