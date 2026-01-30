import { K8sProvider } from '../../lib/providers/K8sProvider.mjs';
import { jest } from '@jest/globals';

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
        expect(mockPortForwarder.forceKillPort).toHaveBeenCalledWith(8092);
        expect(mockPortForwarder.forceKillPort).toHaveBeenCalledWith(8093);
    });

    test('should cleanup tunnels', async () => {
        provider.pids.mapi = 123;
        await provider.cleanup();
        expect(mockPortForwarder.stop).toHaveBeenCalledWith(123);
    });

    test('should setup environment and delegate DB deployment', async () => {
        await provider.setup();
        // Phase 4 Check: Ensure graviteeio repo is added
        expect(mockHelm.repoAdd).toHaveBeenCalledWith('graviteeio', 'https://helm.gravitee.io');
        // Phase 5 Check: Ensure repo update is called
        expect(mockHelm.repoUpdate).toHaveBeenCalled();
        // Phase 3 Check: Use strategy
        expect(mockDatabaseStrategy.deploy).toHaveBeenCalled();
    });

    test('should get license base64 and deploy am with set values', async () => {
        const appVersion = '4.10.0';
        await provider.deploy(appVersion);

        expect(mockLicenseManager.getLicenseBase64).toHaveBeenCalled();
        expect(mockHelm.installOrUpgrade).toHaveBeenCalledWith('am', 'graviteeio/am', expect.objectContaining({
            createNamespace: true,
            version: '4.7.0',
            set: expect.objectContaining({
                'license.key': 'dGVzdC1saWNlbnNl',
                'license.name': 'am-license-v4'
            })
        }));
    });
});
