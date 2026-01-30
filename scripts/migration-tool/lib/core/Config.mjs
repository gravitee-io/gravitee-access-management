import path from 'path';

/**
 * Centralized Configuration Module
 * 
 * Manages environment variables and default values for the migration tool.
 */
export const Config = {
    k8s: {
        namespace: process.env.AM_K8S_NAMESPACE || 'gravitee-am',
        valuesPath: process.env.AM_HELM_VALUES_PATH || 'scripts/migration-tool/env/k8s/values.yaml',
        mongoValuesPath: process.env.AM_MONGO_VALUES_PATH || 'scripts/migration-tool/env/k8s/mongodb-values.yaml',
        amChartVersion: process.env.AM_CHART_VERSION || '4.7.0'
    },
    license: {
        path: process.env.AM_LICENSE_PATH || 'scripts/management/gravitee-universe-v4.key.b64'
    },
    db: {
        type: process.env.AM_DB_TYPE || 'mongodb'
    }
};
