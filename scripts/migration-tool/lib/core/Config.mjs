import path from 'path';

/**
 * Centralized Configuration Module
 * 
 * Manages environment variables and default values for the migration tool.
 */
export const Config = {
    k8s: {
        namespace: process.env.AM_K8S_NAMESPACE || 'gravitee-am',
        valuesPath: process.env.AM_HELM_VALUES_PATH || 'scripts/migration-tool/env/k8s/am/am-mongodb.yaml',
        mongoValuesPath: process.env.AM_MONGO_VALUES_PATH || 'scripts/migration-tool/env/k8s/db/db-mongodb.yaml',
        postgresValuesPath: process.env.AM_POSTGRES_VALUES_PATH || 'scripts/migration-tool/env/k8s/db/db-postgres.yaml',
        postgresChartPath: process.env.AM_POSTGRES_CHART_PATH || 'scripts/migration-tool/env/k8s/db/postgres',
        valuesPathPostgres: process.env.AM_HELM_VALUES_PATH_POSTGRES || 'scripts/migration-tool/env/k8s/am/am-postgres.yaml',
        // Multi-dataplane: 1 API + 2 Gateways per db-type
        valuesPathPostgresMapi: process.env.AM_HELM_VALUES_PATH_POSTGRES_MAPI || 'scripts/migration-tool/env/k8s/am/am-postgres-mapi.yaml',
        valuesPathPostgresGatewayDp1: process.env.AM_HELM_VALUES_PATH_POSTGRES_GW_DP1 || 'scripts/migration-tool/env/k8s/am/am-postgres-gateway-dp1.yaml',
        valuesPathPostgresGatewayDp2: process.env.AM_HELM_VALUES_PATH_POSTGRES_GW_DP2 || 'scripts/migration-tool/env/k8s/am/am-postgres-gateway-dp2.yaml',
        valuesPathMongoMapi: process.env.AM_HELM_VALUES_PATH_MONGO_MAPI || 'scripts/migration-tool/env/k8s/am/am-mongodb-mapi.yaml',
        valuesPathMongoGatewayDp1: process.env.AM_HELM_VALUES_PATH_MONGO_GW_DP1 || 'scripts/migration-tool/env/k8s/am/am-mongodb-gateway-dp1.yaml',
        valuesPathMongoGatewayDp2: process.env.AM_HELM_VALUES_PATH_MONGO_GW_DP2 || 'scripts/migration-tool/env/k8s/am/am-mongodb-gateway-dp2.yaml',
        amChartVersion: process.env.AM_CHART_VERSION || '4.7.0'
    },
    /**
     * Returns Helm release config for K8s multi-dataplane (1 API + 2 Gateways).
     * @param {'mongodb'|'postgres'} dbType
     * @returns {{ name: string, valuesPath: string, component: string }[]}
     */
    getK8sReleases(dbType) {
        const k = Config.k8s;
        if (dbType === 'postgres') {
            return [
                { name: 'am-mapi', valuesPath: k.valuesPathPostgresMapi, component: 'mapi' },
                { name: 'am-gateway-dp1', valuesPath: k.valuesPathPostgresGatewayDp1, component: 'gateway' },
                { name: 'am-gateway-dp2', valuesPath: k.valuesPathPostgresGatewayDp2, component: 'gateway' }
            ];
        }
        return [
            { name: 'am-mapi', valuesPath: k.valuesPathMongoMapi, component: 'mapi' },
            { name: 'am-gateway-dp1', valuesPath: k.valuesPathMongoGatewayDp1, component: 'gateway' },
            { name: 'am-gateway-dp2', valuesPath: k.valuesPathMongoGatewayDp2, component: 'gateway' }
        ];
    },
    license: {
        path: process.env.AM_LICENSE_PATH || 'scripts/management/gravitee-universe-v4.key.b64'
    },
    db: {
        type: process.env.AM_DB_TYPE || 'mongodb'
    },
    /**
     * Test suite directory (relative to CWD or absolute). Override via AM_MIGRATION_TEST_DIR or --test-dir.
     */
    test: {
        dir: process.env.AM_MIGRATION_TEST_DIR || 'gravitee-am-test'
    }
};
