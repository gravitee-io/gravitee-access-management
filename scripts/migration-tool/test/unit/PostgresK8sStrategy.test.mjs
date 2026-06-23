import { PostgresK8sStrategy } from '../../lib/strategies/database/PostgresK8sStrategy.mjs';
import { jest } from '@jest/globals';

describe('PostgresK8sStrategy', () => {
    let strategy;
    let mockHelm;
    let mockConfig;

    beforeEach(() => {
        mockHelm = {
            repoAdd: jest.fn(),
            installOrUpgrade: jest.fn(),
            uninstall: jest.fn()
        };
        mockConfig = {
            k8s: {
                postgresValuesPath: 'scripts/migration-tool/env/k8s/db/db-postgres.yaml',
                postgresChartPath: 'scripts/migration-tool/env/k8s/db/postgres'
            }
        };

        strategy = new PostgresK8sStrategy({
            helm: mockHelm,
            namespace: 'gravitee-am',
            config: mockConfig
        });
    });

    test('should deploy PostgreSQL (local chart)', async () => {
        await strategy.deploy();

        expect(mockHelm.repoAdd).not.toHaveBeenCalled();
        expect(mockHelm.installOrUpgrade).toHaveBeenCalledWith('postgres', 'scripts/migration-tool/env/k8s/db/postgres', expect.objectContaining({
            valuesFile: 'scripts/migration-tool/env/k8s/db/db-postgres.yaml',
            wait: true,
            createNamespace: true
        }));
    });

    test('should clean PostgreSQL', async () => {
        await strategy.clean();
        expect(mockHelm.uninstall).toHaveBeenCalledWith('postgres');
    });

    test('getSeedEnv signals jdbc mode and exposes in-cluster postgres connection for the seed', () => {
        const env = strategy.getSeedEnv();
        expect(env.REPOSITORY_TYPE).toBe('jdbc');
        expect(env.AM_INTERNAL_POSTGRES_HOST).toBe('postgres-postgresql');
        expect(env.AM_INTERNAL_POSTGRES_PORT).toBe('5432');
        expect(env.AM_INTERNAL_POSTGRES_DATABASE).toBe('gravitee-am');
        expect(env.AM_INTERNAL_POSTGRES_USER).toBe('gravitee');
        expect(env.AM_INTERNAL_POSTGRES_PASSWORD).toBe('gravitee-password');
    });

    test('getSeedEnv uses injected gravitee password when provided', () => {
        const prev = process.env.POSTGRES_GRAVITEE_PASSWORD;
        process.env.POSTGRES_GRAVITEE_PASSWORD = 'injected-secret';
        try {
            expect(strategy.getSeedEnv().AM_INTERNAL_POSTGRES_PASSWORD).toBe('injected-secret');
        } finally {
            if (prev !== undefined) process.env.POSTGRES_GRAVITEE_PASSWORD = prev;
            else delete process.env.POSTGRES_GRAVITEE_PASSWORD;
        }
    });
});
