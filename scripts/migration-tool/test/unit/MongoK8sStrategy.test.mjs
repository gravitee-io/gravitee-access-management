import { MongoK8sStrategy } from '../../lib/strategies/database/MongoK8sStrategy.mjs';
import { jest } from '@jest/globals';

/**
 * TDD for MongoK8sStrategy
 */
describe('MongoK8sStrategy', () => {
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
                mongoValuesPath: 'scripts/migration-tool/env/k8s/db/db-mongodb.yaml'
            }
        };
        strategy = new MongoK8sStrategy({
            helm: mockHelm,
            namespace: 'gravitee-am',
            config: mockConfig
        });
    });

    test('should deploy mongo', async () => {
        await strategy.deploy();
        expect(mockHelm.repoAdd).toHaveBeenCalledWith('bitnami', 'https://charts.bitnami.com/bitnami');
        expect(mockHelm.installOrUpgrade).toHaveBeenCalledWith(
            'mongo',
            'bitnami/mongodb',
            expect.objectContaining({
                valuesFile: 'scripts/migration-tool/env/k8s/db/db-mongodb.yaml',
                createNamespace: true,
                wait: true
            })
        );
    });

    test('should clean mongo', async () => {
        await strategy.clean();
        expect(mockHelm.uninstall).toHaveBeenCalledWith('mongo');
    });
});
