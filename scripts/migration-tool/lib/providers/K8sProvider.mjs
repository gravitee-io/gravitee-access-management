import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

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
        this.shell = options.shell ?? (typeof $ !== 'undefined' ? $ : undefined);

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

        this.clusterName = options.clusterName || 'am-migration';
        this.registry = options.registry ? options.registry.replace(/\/+$/, '') : null;
        this.pids = { mapi: null, gatewayDp1: null, gatewayDp1Technical: null, gatewayDp2: null, ui: null };
    }

    /**
     * Ensure a Kubernetes cluster is reachable. If not, create a Kind cluster (used by setup/run so cluster is installed automatically).
     * @throws {Error} If cluster is unreachable and Kind creation fails (e.g. kind not installed, or cluster name already exists).
     */
    async ensureCluster() {
        try {
            await this.kubectl.checkClusterReachable();
            return;
        } catch (_) {
            // Cluster unreachable; try to create Kind cluster.
        }
        if (!this.shell) {
            throw new Error(
                'Kubernetes cluster unreachable and no shell available to create Kind cluster. Start a cluster manually (e.g. kind create cluster --name am-migration).'
            );
        }
        console.log(`🔄 Cluster unreachable. Creating Kind cluster '${this.clusterName}'...`);
        try {
            await this.shell`kind create cluster --name ${this.clusterName} --wait 2m`;
            console.log(`✅ Kind cluster '${this.clusterName}' created.`);
        } catch (e) {
            const msg = (e.stderr ?? e.stdout ?? e.message ?? '').toString();
            if (/already exists/.test(msg)) {
                throw new Error(
                    `Kind cluster '${this.clusterName}' already exists but is unreachable. Delete it and retry: kind delete cluster --name ${this.clusterName}`
                );
            }
            if (/Cannot connect to the Docker daemon|docker daemon running/.test(msg)) {
                throw new Error(
                    'Docker is not running. Kind needs Docker to create clusters. Start Docker Desktop (or the Docker daemon) and retry.'
                );
            }
            if (/command not found|not found|ENOENT/.test(msg) || /kind:/.test(msg)) {
                throw new Error(
                    'Kind is not installed or not on PATH. Install it: https://kind.sigs.k8s.io/docs/user/quick-start/#installation'
                );
            }
            throw new Error(`Failed to create Kind cluster: ${msg.trim() || e.message}`);
        }

        // Use Kind's kubeconfig so kubectl/helm (child processes) talk to the new cluster, not an old context.
        const kubeconfigPath = join(tmpdir(), `am-migration-kind-${this.clusterName}.yaml`);
        const kubeconfigOut = await this.shell`kind get kubeconfig --name ${this.clusterName}`;
        const kubeconfigContent = typeof kubeconfigOut.stdout === 'string' ? kubeconfigOut.stdout : kubeconfigOut.stdout?.toString?.() ?? String(kubeconfigOut);
        writeFileSync(kubeconfigPath, kubeconfigContent.trim());
        process.env.KUBECONFIG = kubeconfigPath;

        // Wait for the API server to be reachable (avoids connection refused on the next clean/setup steps).
        const kindContext = `kind-${this.clusterName}`;
        const maxAttempts = 10;
        const delayMs = 2000;
        for (let attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                await this.kubectl.checkClusterReachable();
                break;
            } catch (err) {
                if (attempt === maxAttempts) {
                    throw new Error(
                        `Kind cluster created but API server not reachable after ${maxAttempts} attempts. Try running: kubectl cluster-info --context ${kindContext}`
                    );
                }
                console.log(`⏳ Waiting for API server (attempt ${attempt}/${maxAttempts})...`);
                await new Promise((r) => setTimeout(r, delayMs));
            }
        }
    }

    /**
     * Delete the Kind cluster (used by teardown). Idempotent: no-op if cluster does not exist or shell unavailable.
     */
    async teardownCluster() {
        if (!this.shell) return;
        console.log(`🗑️  Deleting Kind cluster '${this.clusterName}'...`);
        try {
            await this.shell`kind delete cluster --name ${this.clusterName}`;
            console.log(`✅ Kind cluster '${this.clusterName}' deleted.`);
        } catch (e) {
            const msg = (e.stderr ?? e.stdout ?? e.message ?? '').toString();
            if (/not found|does not exist|No such cluster/.test(msg)) {
                console.log(`⏭️  Kind cluster '${this.clusterName}' not found (already deleted).`);
                return;
            }
            if (/Cannot connect to the Docker daemon|docker daemon running/.test(msg)) {
                console.log('⚠️  Docker not running; cannot delete Kind cluster. Start Docker and run: kind delete cluster --name ' + this.clusterName);
                return;
            }
            throw e;
        }
    }

    /**
     * Environment overrides for the test process when running in this provider (e.g. multi-dataplane ports).
     * Orchestrator merges this into process.env before running tests; test setup files use these or defaults.
     */
    getTestEnv() {
        if (this.releases?.length > 0) {
            return {
                AM_GATEWAY_URL: 'http://localhost:8091',
                AM_DOMAIN_DATA_PLANE_ID: 'dp1',
                AM_GATEWAY_NODE_MONITORING_URL: 'http://localhost:18092/_node',
            };
        }
        return {};
    }

    /**
     * Returns Helm --set overrides for image repositories when a custom registry is configured.
     * @param {'mapi'|'gateway'} component
     */
    _registryOverrides(component) {
        if (!this.registry) return {};
        if (component === 'mapi') {
            return {
                'api.image.repository': `${this.registry}/am-management-api`,
                'gateway.image.repository': `${this.registry}/am-gateway`,
                'ui.image.repository': `${this.registry}/am-management-ui`,
            };
        }
        return {
            'gateway.image.repository': `${this.registry}/am-gateway`,
        };
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
        await this.kubectl.checkClusterReachable();
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
        await this.portForwarder.forceKillPort(18092);

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
        if (this.pids.gatewayDp1Technical) {
            await this.portForwarder.stop(this.pids.gatewayDp1Technical);
            this.pids.gatewayDp1Technical = null;
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
        if (!this.registry) await validateAmImageTag(version);

        const licenseBase64 = await this.licenseManager.getLicenseBase64();

        if (this.releases.length > 0) {
            for (const r of this.releases) {
                const licenseSecretName = `${r.name}-license`;
                const set = {
                    'license.key': licenseBase64,
                    'license.name': licenseSecretName,
                    ...this._registryOverrides(r.component)
                };
                if (r.component === 'mapi') {
                    set['api.image.tag'] = version;
                    set['gateway.image.tag'] = version;
                    set['ui.image.tag'] = version;
                } else {
                    set['gateway.image.tag'] = version;
                }
                const valuesOpt = Array.isArray(r.valuesPath) ? { valuesFiles: r.valuesPath } : { valuesFile: r.valuesPath };
                await this.helm.installOrUpgrade(r.name, 'graviteeio/am', {
                    ...valuesOpt,
                    wait: true,
                    createNamespace: true,
                    version: Config.k8s.amChartVersion,
                    set
                });
            }
        } else {
            const valuesOpt = Array.isArray(this.valuesPath) ? { valuesFiles: this.valuesPath } : { valuesFile: this.valuesPath };
            await this.helm.installOrUpgrade('am', 'graviteeio/am', {
                ...valuesOpt,
                wait: true,
                createNamespace: true,
                version: Config.k8s.amChartVersion,
                set: {
                    'api.image.tag': version,
                    'gateway.image.tag': version,
                    'ui.image.tag': version,
                    'license.key': licenseBase64,
                    'license.name': 'am-license-v4',
                    ...this._registryOverrides('mapi')
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
                this.pids.gatewayDp1Technical = await this.portForwarder.start(`svc/${dp1Release.name}-gateway`, 18092, 18092);
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
        if (!this.registry) await validateAmImageTag(toTag);
        if (this.releases.length > 0) {
            const mapiRelease = this.releases.find(r => r.component === 'mapi');
            if (mapiRelease) {
                const valuesOpt = Array.isArray(mapiRelease.valuesPath) ? { valuesFiles: mapiRelease.valuesPath } : { valuesFile: mapiRelease.valuesPath };
                await this.helm.installOrUpgrade(mapiRelease.name, 'graviteeio/am', {
                    ...valuesOpt,
                    version: Config.k8s.amChartVersion,
                    set: { 'api.image.tag': toTag, 'ui.image.tag': toTag, ...this._registryOverrides('mapi') },
                    reuseValues: true,
                    wait: true
                });
            }
        } else {
            const valuesOpt = Array.isArray(this.valuesPath) ? { valuesFiles: this.valuesPath } : { valuesFile: this.valuesPath };
            await this.helm.installOrUpgrade('am', 'graviteeio/am', {
                ...valuesOpt,
                version: Config.k8s.amChartVersion,
                set: { 'api.image.tag': toTag, 'ui.image.tag': toTag, ...this._registryOverrides('mapi') },
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

    /**
     * Restart gateway port-forwards (e.g. after upgrade-gw so tunnels point at the new pods).
     */
    async restartGatewayTunnels() {
        if (this.pids.gatewayDp1) {
            await this.portForwarder.stop(this.pids.gatewayDp1);
            this.pids.gatewayDp1 = null;
        }
        if (this.pids.gatewayDp1Technical) {
            await this.portForwarder.stop(this.pids.gatewayDp1Technical);
            this.pids.gatewayDp1Technical = null;
        }
        if (this.pids.gatewayDp2) {
            await this.portForwarder.stop(this.pids.gatewayDp2);
            this.pids.gatewayDp2 = null;
        }
        if (this.releases.length > 0) {
            const dp1Release = this.releases.find(r => r.name === 'am-gateway-dp1');
            const dp2Release = this.releases.find(r => r.name === 'am-gateway-dp2');
            if (dp1Release) {
                this.pids.gatewayDp1 = await this.portForwarder.start(`svc/${dp1Release.name}-gateway`, 8091, 82);
                this.pids.gatewayDp1Technical = await this.portForwarder.start(`svc/${dp1Release.name}-gateway`, 18092, 18092);
            }
            if (dp2Release) {
                this.pids.gatewayDp2 = await this.portForwarder.start(`svc/${dp2Release.name}-gateway`, 8092, 82);
            }
        } else {
            this.pids.gatewayDp1 = await this.portForwarder.start('svc/am-gateway', 8092, 82);
            this.pids.gatewayDp2 = null;
        }
    }

    async upgradeGw(toTag) {
        console.log(`🆙 Updating Gateway to ${toTag}...`);
        if (!this.registry) await validateAmImageTag(toTag);
        if (this.releases.length > 0) {
            const gatewayReleases = this.releases.filter(r => r.component === 'gateway');
            for (const r of gatewayReleases) {
                const valuesOpt = Array.isArray(r.valuesPath) ? { valuesFiles: r.valuesPath } : { valuesFile: r.valuesPath };
                await this.helm.installOrUpgrade(r.name, 'graviteeio/am', {
                    ...valuesOpt,
                    version: Config.k8s.amChartVersion,
                    set: { 'gateway.image.tag': toTag, ...this._registryOverrides('gateway') },
                    reuseValues: true,
                    wait: true
                });
            }
        } else {
            const valuesOpt = Array.isArray(this.valuesPath) ? { valuesFiles: this.valuesPath } : { valuesFile: this.valuesPath };
            await this.helm.installOrUpgrade('am', 'graviteeio/am', {
                ...valuesOpt,
                version: Config.k8s.amChartVersion,
                set: { 'gateway.image.tag': toTag, ...this._registryOverrides('gateway') },
                reuseValues: true,
                wait: true
            });
        }
        // Restart gateway port-forwards so they target the new pods; otherwise verify-all gets "socket hang up"
        await this.restartGatewayTunnels();
    }
}
