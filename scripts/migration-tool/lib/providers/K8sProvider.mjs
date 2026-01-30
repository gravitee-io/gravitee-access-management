import { BaseProvider } from './BaseProvider.mjs';
import { Config } from '../core/Config.mjs';
import { Kubectl } from '../infra/kubernetes/Kubectl.mjs';
import { Helm } from '../infra/kubernetes/Helm.mjs';
import { LicenseManager } from '../infra/kubernetes/LicenseManager.mjs';
import { PortForwarder } from '../infra/kubernetes/PortForwarder.mjs';

/**
 * K8sProvider version 2.0 - Refactored for modularity and TDD.
 */
export class K8sProvider extends BaseProvider {
    constructor(options) {
        super(options);
        this.namespace = options.namespace || Config.k8s.namespace;

        // Dependency Injection: Database Strategy
        this.databaseStrategy = options.databaseStrategy;
        if (!this.databaseStrategy) {
            throw new Error('DatabaseStrategy is required');
        }

        // Infrastructure Dependencies (Injected or Default)
        this.kubectl = options.kubectl || new Kubectl({ namespace: this.namespace });
        this.helm = options.helm || new Helm({ namespace: this.namespace });
        this.licenseManager = options.licenseManager || new LicenseManager({
            namespace: this.namespace,
            kubectl: this.kubectl,
            localLicensePath: options.localLicensePath || Config.license.path
        });
        this.portForwarder = options.portForwarder || new PortForwarder({ namespace: this.namespace });

        this.valuesPath = options.valuesPath || Config.k8s.valuesPath;
        // this.mongoValuesPath removed (managed by strategy)

        this.pids = { mapi: null, gateway: null };
    }

    async setup() {
        console.log('🏗️  Setting up K8s environment...');

        // Ensure Repositories
        await this.helm.repoAdd('graviteeio', 'https://helm.gravitee.io');
        await this.helm.repoUpdate();
        // mongo repo is handled by strategy

        // Database Deployment
        await this.databaseStrategy.deploy();
    }

    async clean() {
        console.log('🧹 Cleaning up K8s environment...');

        console.log('🗑️  Uninstalling Helm releases...');
        await this.helm.uninstall('am');
        await this.databaseStrategy.clean();

        console.log('🗝️  Cleaning up secrets...');
        await this.kubectl.deleteSecret('am-license');
        await this.kubectl.deleteSecret('am-license-v4');
        await this.kubectl.deleteSecret('licensekey-am');

        await this.portForwarder.forceKillPort(8092);
        await this.portForwarder.forceKillPort(8093);

        console.log(`💀 Deleting namespace: ${this.namespace}...`);
        await this.kubectl.deleteNamespace();

        await this.cleanup();
        console.log('✅ Cleanup complete.');
    }

    async cleanup() {
        if (this.pids.mapi) {
            await this.portForwarder.stop(this.pids.mapi);
            this.pids.mapi = null;
        }
        if (this.pids.gateway) {
            await this.portForwarder.stop(this.pids.gateway);
            this.pids.gateway = null;
        }
    }

    async deploy(version) {
        console.log(`🚀 Deploying AM version ${version}...`);

        const licenseBase64 = await this.licenseManager.getLicenseBase64();

        await this.helm.installOrUpgrade('am', 'graviteeio/am', {
            valuesFile: this.valuesPath,
            wait: true,
            createNamespace: true,
            version: Config.k8s.amChartVersion,
            set: {
                'api.image.tag': version,
                'gateway.image.tag': version,
                'license.key': licenseBase64,
                'license.name': 'am-license-v4'
            }
        });

        await this.startTunnels();
    }

    async startTunnels() {
        this.pids.mapi = await this.portForwarder.start('svc/am-management-api', 8093, 83);
        this.pids.gateway = await this.portForwarder.start('svc/am-gateway', 8092, 82);
    }

    // Remaining methods will be refactored as needed
    async verifyBaseline() {
        console.log('🔍 Verifying baseline...');
        // Implement verification logic using management API client
    }
}
