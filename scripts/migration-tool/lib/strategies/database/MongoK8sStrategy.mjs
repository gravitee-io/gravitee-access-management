import { DatabaseStrategy } from './DatabaseStrategy.mjs';

/**
 * MongoDB Strategy for Kubernetes
 */
export class MongoK8sStrategy extends DatabaseStrategy {
    constructor(options) {
        super(options.config);
        this.helm = options.helm;
        this.namespace = options.namespace;
        this.repoName = 'bitnami';
        this.repoUrl = 'https://charts.bitnami.com/bitnami';
        this.releaseName = 'mongo';
        this.chartName = 'bitnami/mongodb';
    }

    async deploy() {
        console.log('🍃 Deploying standalone MongoDB...');
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
    }

    async waitForReady() {
        if (!this.kubectl) return;
        await this.kubectl.waitForPodReady('app.kubernetes.io/name=mongodb', 120);
    }
}
