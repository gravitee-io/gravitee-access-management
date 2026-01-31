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
});
