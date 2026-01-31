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

    async configMapExists(name) {
        try {
            await this.shell`kubectl get configmap ${name} -n ${this.namespace}`;
            return true;
        } catch (e) {
            return false;
        }
    }

    async createConfigMapFromFile(name, filePath) {
        await this.shell`kubectl create configmap ${name} -n ${this.namespace} --from-file=${filePath}`;
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

    /**
     * Wait for at least one pod matching the label selector to be ready.
     * @param {string} selector - e.g. 'app.kubernetes.io/name=mongodb'
     * @param {number} [timeoutSeconds=120]
     * @throws if no pod becomes ready within timeout
     */
    async waitForPodReady(selector, timeoutSeconds = 120) {
        await this.shell`kubectl wait --for=condition=ready pod -l ${selector} -n ${this.namespace} --timeout=${timeoutSeconds}s`;
    }
}
