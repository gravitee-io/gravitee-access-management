/**
 * Kubectl specialized wrapper for Gravitee AM migration tool.
 */
export class Kubectl {
    constructor(options) {
        this.namespace = options.namespace;
        this.shell = options.shell || $; // Use global zx shell by default
    }

    /**
     * Verify the cluster from kubeconfig is reachable. Call before clean/setup to fail fast with a clear message.
     * @throws {Error} If cluster is unreachable (e.g. Kind not running)
     */
    async checkClusterReachable() {
        try {
            await this.shell`kubectl cluster-info --request-timeout=5s`;
        } catch (e) {
            throw new Error(
                'Kubernetes cluster unreachable. Start a local cluster first (e.g. kind create cluster --name am-migration).'
            );
        }
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

    /**
     * Ensure the namespace exists (idempotent).
     */
    async ensureNamespace() {
        await this.shell`kubectl create namespace ${this.namespace} --dry-run=client -o yaml | kubectl apply -f -`;
    }

    /**
     * Create a generic secret from literal key-value pairs (for Bitnami MongoDB auth.existingSecret).
     * @param {string} name - Secret name
     * @param {Record<string, string>} literals - Keys and string values (no sensitive data in logs)
     */
    async createSecretGeneric(name, literals) {
        const args = Object.entries(literals).flatMap(([k, v]) => ['--from-literal', `${k}=${v}`]);
        await this.shell`kubectl create secret generic ${name} -n ${this.namespace} ${args} --dry-run=client -o yaml | kubectl apply -f -`;
    }

    async deleteNamespace() {
        await this.shell`kubectl delete namespace ${this.namespace} --ignore-not-found`;
    }

    async getPods(selector) {
        const result = await this.shell`kubectl get pods -n ${this.namespace} -l ${selector} -o json`;
        return JSON.parse(result.stdout).items;
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
