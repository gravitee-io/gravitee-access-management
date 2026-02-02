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
            // Bitnami chart expects one password per user (parallel to auth.usernames); comma-separated when multiple; same password for gravitee on all three DBs
            const passwordsForUsers = [graviteePassword, graviteePassword, graviteePassword].join(',');
            await this.kubectl.createSecretGeneric(MONGODB_AUTH_SECRET_NAME, {
                'mongodb-root-password': rootPassword,
                'mongodb-passwords': passwordsForUsers
            });
        }

        await this.helm.repoAdd(this.repoName, this.repoUrl);

        await this.helm.installOrUpgrade(this.releaseName, this.chartName, {
            valuesFile: this.config.k8s.mongoValuesPath,
            wait: true,
            createNamespace: true
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
}
