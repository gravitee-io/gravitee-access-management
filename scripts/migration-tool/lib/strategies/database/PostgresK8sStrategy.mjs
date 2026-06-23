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

    /**
     * Connection details for the seed's custom JDBC identity provider, plus the REPOSITORY_TYPE
     * discriminator the seed reads to build a Postgres (rather than Mongo) IdP. Consumed by the
     * AM pods (not the test process), so the host uses in-cluster DNS (postgres-postgresql) and
     * the gravitee user's credentials against a database that user owns (gravitee-am).
     */
    getSeedEnv() {
        const password = process.env.POSTGRES_GRAVITEE_PASSWORD || 'gravitee-password';
        return {
            REPOSITORY_TYPE: 'jdbc',
            AM_INTERNAL_POSTGRES_HOST: 'postgres-postgresql',
            AM_INTERNAL_POSTGRES_PORT: '5432',
            AM_INTERNAL_POSTGRES_DATABASE: 'gravitee-am',
            AM_INTERNAL_POSTGRES_USER: 'gravitee',
            AM_INTERNAL_POSTGRES_PASSWORD: password,
        };
    }
}
