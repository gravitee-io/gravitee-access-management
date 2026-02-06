import { DatabaseStrategy } from './DatabaseStrategy.mjs';

/**
 * PostgreSQL Strategy for Kubernetes.
 * Uses minimal internal chart (postgres:16.6) so md5() works; no Bitnami chart/image.
 */
export class PostgresK8sStrategy extends DatabaseStrategy {
    constructor(options) {
        super(options.config);
        this.helm = options.helm;
        this.namespace = options.namespace;
        this.kubectl = options.kubectl;
        this.releaseName = 'postgres';
        this.chartPath = options.chartPath || this.config.k8s.postgresChartPath;
    }

    async deploy() {
        console.log('🐘 Deploying standalone PostgreSQL (postgres:16.6)...');
        await this.helm.installOrUpgrade(this.releaseName, this.chartPath, {
            valuesFile: this.config.k8s.postgresValuesPath,
            wait: true,
            createNamespace: true
        });
    }

    async clean() {
        console.log('🧹 Cleaning up PostgreSQL...');
        await this.helm.uninstall(this.releaseName);
    }

    async waitForReady() {
        if (!this.kubectl) return;
        await this.kubectl.waitForPodReady('app.kubernetes.io/name=postgres', 120);
    }
}
