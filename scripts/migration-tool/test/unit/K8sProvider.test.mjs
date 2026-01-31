import { jest } from '@jest/globals';

jest.unstable_mockModule('../../lib/core/VersionValidator.mjs', () => ({
    validateAmImageTag: jest.fn().mockResolvedValue(undefined)
}));

const { K8sProvider } = await import('../../lib/providers/K8sProvider.mjs');

/**
 * TDD for Refactored K8sProvider
 */
describe('K8sProvider', () => {
    let provider;
    let mockHelm;
    let mockKubectl;
    let mockLicenseManager;
    let mockPortForwarder;
    let mockDatabaseStrategy;

    beforeEach(() => {
        mockHelm = {
            repoAdd: jest.fn(),
            repoUpdate: jest.fn(),
            installOrUpgrade: jest.fn(),
            uninstall: jest.fn()
        };
        mockKubectl = {
            secretExists: jest.fn(),
            deleteSecret: jest.fn(),
            deleteNamespace: jest.fn(),
            createSecretFromFile: jest.fn()
        };
        mockLicenseManager = {
            getLicenseBase64: jest.fn().mockResolvedValue('dGVzdC1saWNlbnNl')
        };
        mockPortForwarder = {
            start: jest.fn(),
            stop: jest.fn(),
            forceKillPort: jest.fn()
        };
        mockDatabaseStrategy = {
            deploy: jest.fn(),
            clean: jest.fn(),
            waitForReady: jest.fn()
        };

        provider = new K8sProvider({
            namespace: 'gravitee-am',
            valuesPath: 'scripts/migration-tool/env/k8s/am/am-mongodb.yaml',
            helm: mockHelm,
            kubectl: mockKubectl,
            licenseManager: mockLicenseManager,
            portForwarder: mockPortForwarder,
            databaseStrategy: mockDatabaseStrategy
        });
    });

    test('should clean heritage resources and delegate to strategy', async () => {
        provider.pids.mapi = 123;
        await provider.clean();
        expect(mockHelm.uninstall).toHaveBeenCalledWith('am');
        expect(mockDatabaseStrategy.clean).toHaveBeenCalled();
        expect(mockKubectl.deleteNamespace).toHaveBeenCalled();
        expect(mockKubectl.deleteSecret).toHaveBeenCalledWith('am-license');
        expect(mockPortForwarder.stop).toHaveBeenCalledWith(123);
        expect(mockPortForwarder.forceKillPort).toHaveBeenCalledWith(8091);
        expect(mockPortForwarder.forceKillPort).toHaveBeenCalledWith(8092);
        expect(mockPortForwarder.forceKillPort).toHaveBeenCalledWith(8093);
        expect(mockPortForwarder.forceKillPort).toHaveBeenCalledWith(8002);
    });

    test('should cleanup tunnels', async () => {
        provider.pids.mapi = 123;
        provider.pids.gatewayDp1 = 456;
        await provider.cleanup();
        expect(mockPortForwarder.stop).toHaveBeenCalledWith(123);
        expect(mockPortForwarder.stop).toHaveBeenCalledWith(456);
    });

    test('should setup environment and delegate DB deployment', async () => {
        await provider.setup();
        expect(mockHelm.repoAdd).toHaveBeenCalledWith('graviteeio', 'https://helm.gravitee.io');
        expect(mockHelm.repoUpdate).toHaveBeenCalled();
        expect(mockDatabaseStrategy.deploy).toHaveBeenCalled();
        expect(mockDatabaseStrategy.waitForReady).toHaveBeenCalled();
    });

    test('should get license base64 and deploy am with set values', async () => {
        const appVersion = '4.10.0';
        await provider.deploy(appVersion);

        expect(mockLicenseManager.getLicenseBase64).toHaveBeenCalled();
        expect(mockHelm.installOrUpgrade).toHaveBeenCalledWith('am', 'graviteeio/am', expect.objectContaining({
            valuesFile: 'scripts/migration-tool/env/k8s/am/am-mongodb.yaml',
            createNamespace: true,
            version: '4.7.0',
            set: expect.objectContaining({
                'license.key': 'dGVzdC1saWNlbnNl',
                'license.name': 'am-license-v4'
            })
        }));
    });
});

describe('K8sProvider with releases (multi-dataplane)', () => {
    let provider;
    let mockHelm;
    let mockKubectl;
    let mockPortForwarder;
    let mockDatabaseStrategy;

    const releases = [
        { name: 'am-mapi', valuesPath: '/am/am-mapi.yaml', component: 'mapi' },
        { name: 'am-gateway-dp1', valuesPath: '/am/am-gw-dp1.yaml', component: 'gateway' },
        { name: 'am-gateway-dp2', valuesPath: '/am/am-gw-dp2.yaml', component: 'gateway' }
    ];

    beforeEach(() => {
        mockHelm = {
            repoAdd: jest.fn(),
            repoUpdate: jest.fn(),
            installOrUpgrade: jest.fn(),
            uninstall: jest.fn()
        };
        mockKubectl = { deleteSecret: jest.fn(), deleteNamespace: jest.fn() };
        mockPortForwarder = {
            start: jest.fn().mockResolvedValue(999),
            stop: jest.fn(),
            forceKillPort: jest.fn()
        };
        mockDatabaseStrategy = {
            deploy: jest.fn(),
            clean: jest.fn(),
            waitForReady: jest.fn()
        };

        provider = new K8sProvider({
            namespace: 'gravitee-am',
            helm: mockHelm,
            kubectl: mockKubectl,
            licenseManager: { getLicenseBase64: jest.fn().mockResolvedValue('dGVzdC1saWNlbnNl') },
            portForwarder: mockPortForwarder,
            databaseStrategy: mockDatabaseStrategy,
            releases
        });
    });

    test('clean should uninstall all three releases, delete per-release license secrets, and kill ports', async () => {
        await provider.clean();
        expect(mockHelm.uninstall).toHaveBeenCalledWith('am-mapi');
        expect(mockHelm.uninstall).toHaveBeenCalledWith('am-gateway-dp1');
        expect(mockHelm.uninstall).toHaveBeenCalledWith('am-gateway-dp2');
        expect(mockHelm.uninstall).toHaveBeenCalledTimes(3);
        expect(mockKubectl.deleteSecret).toHaveBeenCalledWith('am-mapi-license');
        expect(mockKubectl.deleteSecret).toHaveBeenCalledWith('am-gateway-dp1-license');
        expect(mockKubectl.deleteSecret).toHaveBeenCalledWith('am-gateway-dp2-license');
        expect(mockPortForwarder.forceKillPort).toHaveBeenCalledWith(8091);
        expect(mockPortForwarder.forceKillPort).toHaveBeenCalledWith(8092);
        expect(mockPortForwarder.forceKillPort).toHaveBeenCalledWith(8093);
        expect(mockPortForwarder.forceKillPort).toHaveBeenCalledWith(8002);
    });

    test('deploy should installOrUpgrade three releases with per-release license secret name', async () => {
        await provider.deploy('4.10.0');
        expect(mockHelm.installOrUpgrade).toHaveBeenCalledTimes(3);
        expect(mockHelm.installOrUpgrade).toHaveBeenNthCalledWith(1, 'am-mapi', 'graviteeio/am', expect.objectContaining({
            valuesFile: '/am/am-mapi.yaml',
            set: expect.objectContaining({
                'api.image.tag': '4.10.0',
                'gateway.image.tag': '4.10.0',
                'license.name': 'am-mapi-license'
            })
        }));
        expect(mockHelm.installOrUpgrade).toHaveBeenNthCalledWith(2, 'am-gateway-dp1', 'graviteeio/am', expect.objectContaining({
            valuesFile: '/am/am-gw-dp1.yaml',
            set: expect.objectContaining({
                'gateway.image.tag': '4.10.0',
                'license.name': 'am-gateway-dp1-license'
            })
        }));
        expect(mockHelm.installOrUpgrade).toHaveBeenNthCalledWith(3, 'am-gateway-dp2', 'graviteeio/am', expect.objectContaining({
            valuesFile: '/am/am-gw-dp2.yaml',
            set: expect.objectContaining({
                'gateway.image.tag': '4.10.0',
                'license.name': 'am-gateway-dp2-license'
            })
        }));
    });

    test('startTunnels should port-forward mapi, dp1 (8091), dp2 (8092), ui', async () => {
        await provider.startTunnels();
        expect(mockPortForwarder.start).toHaveBeenCalledWith('svc/am-mapi-management-api', 8093, 83);
        expect(mockPortForwarder.start).toHaveBeenCalledWith('svc/am-mapi-management-ui', 8002, 8002);
        expect(mockPortForwarder.start).toHaveBeenCalledWith('svc/am-gateway-dp1-gateway', 8091, 82);
        expect(mockPortForwarder.start).toHaveBeenCalledWith('svc/am-gateway-dp2-gateway', 8092, 82);
        expect(mockPortForwarder.start).toHaveBeenCalledTimes(4);
    });
});
