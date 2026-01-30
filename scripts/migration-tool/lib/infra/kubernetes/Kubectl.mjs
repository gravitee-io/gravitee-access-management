/**
 * Kubectl specialized wrapper for Gravitee AM migration tool.
 */
export class Kubectl {
    constructor(options) {
        this.namespace = options.namespace;
        this.shell = options.shell || $; // Use global zx shell by default
    }

    async secretExists(name) {
        try {
            await this.shell`kubectl get secret ${name} -n ${this.namespace}`;
            return true;
        } catch (e) {
            return false;
        }
    }

    async createSecretFromFile(name, filePath, keyName = 'license') {
        await this.shell`kubectl create secret generic ${name} -n ${this.namespace} --from-file=${keyName}=${filePath}`;
    }

    async deleteSecret(name) {
        await this.shell`kubectl delete secret ${name} -n ${this.namespace} --ignore-not-found`;
    }

    async deleteNamespace() {
        await this.shell`kubectl delete namespace ${this.namespace} --ignore-not-found`;
    }

    async getPods(selector) {
        const result = await this.shell`kubectl get pods -n ${this.namespace} -l ${selector} -o json`;
        return JSON.parse(result.stdout).items;
    }

    async getEvents(limit = 10) {
        return await this.shell`kubectl get events -n ${this.namespace} --sort-by='.lastTimestamp' | tail -n ${limit}`;
    }
}
