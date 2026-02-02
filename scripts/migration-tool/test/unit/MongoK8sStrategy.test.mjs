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

    test('when kubectl is provided, should ensure namespace and create auth secret before deploy', async () => {
        const mockKubectl = {
            ensureNamespace: jest.fn(),
            createSecretGeneric: jest.fn(),
            deleteSecret: jest.fn()
        };
        strategy = new MongoK8sStrategy({
            helm: mockHelm,
            namespace: 'gravitee-am',
            config: mockConfig,
            kubectl: mockKubectl
        });
        await strategy.deploy();
        expect(mockKubectl.ensureNamespace).toHaveBeenCalled();
        expect(mockKubectl.createSecretGeneric).toHaveBeenCalledWith(
            'mongo-mongodb-auth',
            expect.objectContaining({
                'mongodb-root-password': expect.any(String),
                'mongodb-passwords': 'gravitee-password,gravitee-password,gravitee-password'
            })
        );
        expect(mockHelm.installOrUpgrade).toHaveBeenCalled();
    });

    test('when kubectl is provided, clean should delete auth secret', async () => {
        const mockKubectl = {
            ensureNamespace: jest.fn(),
            createSecretGeneric: jest.fn(),
            deleteSecret: jest.fn()
        };
        strategy = new MongoK8sStrategy({
            helm: mockHelm,
            namespace: 'gravitee-am',
            config: mockConfig,
            kubectl: mockKubectl
        });
        await strategy.clean();
        expect(mockHelm.uninstall).toHaveBeenCalledWith('mongo');
        expect(mockKubectl.deleteSecret).toHaveBeenCalledWith('mongo-mongodb-auth');
    });

    test('when MONGODB_GRAVITEE_PASSWORD is set, secret uses injected password', async () => {
        const prev = process.env.MONGODB_GRAVITEE_PASSWORD;
        process.env.MONGODB_GRAVITEE_PASSWORD = 'injected-secret';
        const mockKubectl = {
            ensureNamespace: jest.fn(),
            createSecretGeneric: jest.fn(),
            deleteSecret: jest.fn()
        };
        strategy = new MongoK8sStrategy({
            helm: mockHelm,
            namespace: 'gravitee-am',
            config: mockConfig,
            kubectl: mockKubectl
        });
        try {
            await strategy.deploy();
            expect(mockKubectl.createSecretGeneric).toHaveBeenCalledWith(
                'mongo-mongodb-auth',
                expect.objectContaining({
                    'mongodb-passwords': 'injected-secret,injected-secret,injected-secret'
                })
            );
        } finally {
            if (prev !== undefined) process.env.MONGODB_GRAVITEE_PASSWORD = prev;
            else delete process.env.MONGODB_GRAVITEE_PASSWORD;
        }
    });
});
