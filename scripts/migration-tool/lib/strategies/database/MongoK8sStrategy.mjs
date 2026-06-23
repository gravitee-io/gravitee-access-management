import { DatabaseStrategy } from './DatabaseStrategy.mjs';

/**
 * MongoDB Strategy for Kubernetes
 */
/** Secret name used by Bitnami MongoDB chart when auth.existingSecret is set (no password in values YAML). */
const MONGODB_AUTH_SECRET_NAME = 'mongo-mongodb-auth';

export class MongoK8sStrategy extends DatabaseStrategy {
    constructor(options) {
        super(options.config);
        this.helm = options.helm;
        this.kubectl = options.kubectl;
        this.namespace = options.namespace;
        this.repoName = 'bitnami';
        this.repoUrl = 'https://charts.bitnami.com/bitnami';
        this.releaseName = 'mongo';
        this.chartName = 'bitnami/mongodb';
    }

    async deploy() {
        console.log('🍃 Deploying standalone MongoDB...');

        if (this.kubectl) {
            await this.kubectl.ensureNamespace();
            const rootPassword = process.env.MONGODB_ROOT_PASSWORD || 'migration-test-only';
            // Inject via env in CI to avoid hardcoded secrets (Aqua scan); default for local only.
            const graviteePassword = process.env.MONGODB_GRAVITEE_PASSWORD || 'gravitee-password';
            // Single 'gravitee' user (parallel to auth.usernames in db-mongodb.yaml). Access to the
            // dataplane DBs (gravitee-dp1/dp2) is granted via initdbScripts, so only one password is needed.
            await this.kubectl.createSecretGeneric(MONGODB_AUTH_SECRET_NAME, {
                'mongodb-root-password': rootPassword,
                'mongodb-passwords': graviteePassword
            });
        }

        await this.helm.repoAdd(this.repoName, this.repoUrl);

        await this.helm.installOrUpgrade(this.releaseName, this.chartName, {
            valuesFile: this.config.k8s.mongoValuesPath,
            wait: true,
            createNamespace: true,
            timeout: '10m',
            set: { namespaceOverride: this.namespace }
        });
    }

    async clean() {
        console.log('🧹 Cleaning up MongoDB...');
        await this.helm.uninstall(this.releaseName);
        if (this.kubectl) {
            await this.kubectl.deleteSecret(MONGODB_AUTH_SECRET_NAME);
        }
    }

    async waitForReady() {
        if (!this.kubectl) return;
        await this.kubectl.waitForPodReady('app.kubernetes.io/name=mongodb', 120);
    }

    /**
     * Connection details for the seed's custom Mongo identity provider.
     * Consumed by the AM pods (not the test process), so it must use in-cluster DNS
     * (mongo-mongodb), the gravitee user's credentials, and a database that user can
     * access (gravitee/gravitee-dp1/gravitee-dp2 per db-mongodb.yaml).
     */
    getSeedEnv() {
        const graviteePassword = process.env.MONGODB_GRAVITEE_PASSWORD || 'gravitee-password';
        return {
            AM_INTERNAL_MONGODB_URI: `mongodb://gravitee:${graviteePassword}@mongo-mongodb:27017/gravitee?serverSelectionTimeoutMS=30000`,
            AM_INTERNAL_MONGODB_DATABASE: 'gravitee',
        };
    }
}
