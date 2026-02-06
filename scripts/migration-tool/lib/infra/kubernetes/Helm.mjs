/**
 * Helm specialized wrapper for Gravitee AM migration tool.
 */
export class Helm {
    constructor(options) {
        this.namespace = options.namespace;
        this.shell = options.shell || $;
    }

    async repoAdd(name, url) {
        await this.shell`helm repo add ${name} ${url}`;
    }

    async repoUpdate() {
        await this.shell`helm repo update`;
    }

    async installOrUpgrade(releaseName, chartName, options = {}) {
        const { valuesFile, valuesFiles, wait = false, createNamespace = false, version, set = {}, reuseValues = false } = options;
        const flags = ['--install', releaseName, chartName, '-n', this.namespace];

        if (valuesFiles?.length) {
            for (const f of valuesFiles) flags.push('-f', f);
        } else if (valuesFile) {
            flags.push('-f', valuesFile);
        }
        if (wait) flags.push('--wait');
        if (createNamespace) flags.push('--create-namespace');
        if (reuseValues) flags.push('--reuse-values');
        if (version) flags.push('--version', version);

        for (const [key, value] of Object.entries(set)) {
            flags.push('--set', `${key}=${value}`);
        }

        await this.shell`helm upgrade ${flags}`;
    }

    async uninstall(releaseName) {
        await this.shell`helm uninstall ${releaseName} -n ${this.namespace} --ignore-not-found`;
    }
}
