import { BaseProvider } from './BaseProvider.mjs';
import { Config } from '../core/Config.mjs';
import { validateAmImageTag } from '../core/VersionValidator.mjs';
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

        // Multi-dataplane: list of { name, valuesPath, component } (am-mapi, am-gateway-dp1, am-gateway-dp2)
        this.releases = options.releases || [];
        this.valuesPath = options.valuesPath || Config.k8s.valuesPath;

        this.pids = { mapi: null, gatewayDp1: null, gatewayDp2: null, ui: null };
    }

    async setup() {
        console.log('🏗️  Setting up K8s environment...');

        // Ensure Repositories
        await this.helm.repoAdd('graviteeio', 'https://helm.gravitee.io');
        await this.helm.repoUpdate();
        // mongo repo is handled by strategy

        // Database Deployment
        await this.databaseStrategy.deploy();
        await this.databaseStrategy.waitForReady();
    }

    async clean() {
        console.log('🧹 Cleaning up K8s environment...');

        console.log('🗑️  Uninstalling Helm releases...');
        if (this.releases.length > 0) {
            for (const r of this.releases) {
                await this.helm.uninstall(r.name);
            }
        } else {
            await this.helm.uninstall('am');
        }
        await this.databaseStrategy.clean();

        console.log('🗝️  Cleaning up secrets...');
        if (this.releases.length > 0) {
            for (const r of this.releases) {
                await this.kubectl.deleteSecret(`${r.name}-license`);
            }
        }
        await this.kubectl.deleteSecret('am-license');
        await this.kubectl.deleteSecret('am-license-v4');
        await this.kubectl.deleteSecret('licensekey-am');

        await this.portForwarder.forceKillPort(8091);
        await this.portForwarder.forceKillPort(8092);
        await this.portForwarder.forceKillPort(8093);
        await this.portForwarder.forceKillPort(8002);

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
        if (this.pids.gatewayDp1) {
            await this.portForwarder.stop(this.pids.gatewayDp1);
            this.pids.gatewayDp1 = null;
        }
        if (this.pids.gatewayDp2) {
            await this.portForwarder.stop(this.pids.gatewayDp2);
            this.pids.gatewayDp2 = null;
        }
        if (this.pids.ui) {
            await this.portForwarder.stop(this.pids.ui);
            this.pids.ui = null;
        }
    }

    async deploy(version) {
        console.log(`🚀 Deploying AM version ${version}...`);
        await validateAmImageTag(version);

        const licenseBase64 = await this.licenseManager.getLicenseBase64();

        if (this.releases.length > 0) {
            for (const r of this.releases) {
                const licenseSecretName = `${r.name}-license`;
                const set = {
                    'license.key': licenseBase64,
                    'license.name': licenseSecretName
                };
                if (r.component === 'mapi') {
                    set['api.image.tag'] = version;
                    set['gateway.image.tag'] = version;
                } else {
                    set['gateway.image.tag'] = version;
                }
                await this.helm.installOrUpgrade(r.name, 'graviteeio/am', {
                    valuesFile: r.valuesPath,
                    wait: true,
                    createNamespace: true,
                    version: Config.k8s.amChartVersion,
                    set
                });
            }
        } else {
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
        }

        await this.startTunnels();
    }

    async startTunnels() {
        if (this.releases.length > 0) {
            const mapiRelease = this.releases.find(r => r.component === 'mapi');
            const dp1Release = this.releases.find(r => r.name === 'am-gateway-dp1');
            const dp2Release = this.releases.find(r => r.name === 'am-gateway-dp2');
            if (mapiRelease) {
                this.pids.mapi = await this.portForwarder.start(`svc/${mapiRelease.name}-management-api`, 8093, 83);
                this.pids.ui = await this.portForwarder.start(`svc/${mapiRelease.name}-management-ui`, 8002, 8002);
            }
            if (dp1Release) {
                this.pids.gatewayDp1 = await this.portForwarder.start(`svc/${dp1Release.name}-gateway`, 8091, 82);
            }
            if (dp2Release) {
                this.pids.gatewayDp2 = await this.portForwarder.start(`svc/${dp2Release.name}-gateway`, 8092, 82);
            }
        } else {
            this.pids.mapi = await this.portForwarder.start('svc/am-management-api', 8093, 83);
            this.pids.gatewayDp1 = await this.portForwarder.start('svc/am-gateway', 8092, 82);
            this.pids.gatewayDp2 = null;
            this.pids.ui = await this.portForwarder.start('svc/am-management-ui', 8002, 8002);
        }
    }

    async upgradeMapi(toTag) {
        console.log(`🆙 Updating Management API to ${toTag}...`);
        await validateAmImageTag(toTag);
        if (this.releases.length > 0) {
            const mapiRelease = this.releases.find(r => r.component === 'mapi');
            if (mapiRelease) {
                await this.helm.installOrUpgrade(mapiRelease.name, 'graviteeio/am', {
                    valuesFile: mapiRelease.valuesPath,
                    version: Config.k8s.amChartVersion,
                    set: { 'api.image.tag': toTag },
                    reuseValues: true,
                    wait: true
                });
            }
        } else {
            await this.helm.installOrUpgrade('am', 'graviteeio/am', {
                valuesFile: this.valuesPath,
                version: Config.k8s.amChartVersion,
                set: { 'api.image.tag': toTag },
                reuseValues: true,
                wait: true
            });
        }
        // Restart API/UI port-forwards so they target the new pod; otherwise verify-mapi gets "socket hang up"
        await this.restartApiTunnels();
    }

    /**
     * Restart API and UI port-forwards (e.g. after upgrade-mapi so tunnels point at the new pod).
     */
    async restartApiTunnels() {
        if (this.pids.mapi) {
            await this.portForwarder.stop(this.pids.mapi);
            this.pids.mapi = null;
        }
        if (this.pids.ui) {
            await this.portForwarder.stop(this.pids.ui);
            this.pids.ui = null;
        }
        if (this.releases.length > 0) {
            const mapiRelease = this.releases.find(r => r.component === 'mapi');
            if (mapiRelease) {
                this.pids.mapi = await this.portForwarder.start(`svc/${mapiRelease.name}-management-api`, 8093, 83);
                this.pids.ui = await this.portForwarder.start(`svc/${mapiRelease.name}-management-ui`, 8002, 8002);
            }
        } else {
            this.pids.mapi = await this.portForwarder.start('svc/am-management-api', 8093, 83);
            this.pids.ui = await this.portForwarder.start('svc/am-management-ui', 8002, 8002);
        }
    }

    async upgradeGw(toTag) {
        console.log(`🆙 Updating Gateway to ${toTag}...`);
        await validateAmImageTag(toTag);
        if (this.releases.length > 0) {
            const gatewayReleases = this.releases.filter(r => r.component === 'gateway');
            for (const r of gatewayReleases) {
                await this.helm.installOrUpgrade(r.name, 'graviteeio/am', {
                    valuesFile: r.valuesPath,
                    version: Config.k8s.amChartVersion,
                    set: { 'gateway.image.tag': toTag },
                    reuseValues: true,
                    wait: true
                });
            }
        } else {
            await this.helm.installOrUpgrade('am', 'graviteeio/am', {
                valuesFile: this.valuesPath,
                version: Config.k8s.amChartVersion,
                set: { 'gateway.image.tag': toTag },
                reuseValues: true,
                wait: true
            });
        }
    }

    async prepareTests() {
        console.log('📦 Preparing test environment...');
        // No-op for now as ci.setup.js has correct defaults for local tunnels.
        // We could add npm install check here if needed.
    }

    async verifyBaseline() {
        console.log('🔍 Verifying baseline health...');
        // Simple health check to ensure API is up before Orchestrator runs full Jests
        try {
            await this.shell`curl -sSf http://localhost:8093/management/health`.quiet();
            console.log('✅ API is healthy');
        } catch (e) {
            throw new Error('Baseline health check failed: Management API is not responding on 8093');
        }
    }
}
