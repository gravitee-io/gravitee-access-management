import fs from 'fs';

/**
 * LicenseManager handles Gravitee license detection for K8s. License is passed to Helm via --set in K8sProvider.deploy().
 */
export class LicenseManager {
    constructor(options) {
        this.namespace = options.namespace;
        this.kubectl = options.kubectl;
        this.localLicensePath = options.localLicensePath;
    }

    async getLicenseContent() {
        if (process.env.GRAVITEE_LICENSE) {
            const content = process.env.GRAVITEE_LICENSE.trim();
            if (/^[A-Za-z0-9+/=]+$/.test(content) && content.length > 20) {
                return Buffer.from(content, 'base64');
            }
            return content;
        }

        if (this.localLicensePath && fs.existsSync(this.localLicensePath)) {
            const content = fs.readFileSync(this.localLicensePath);
            if (this.localLicensePath.endsWith('.b64')) {
                return Buffer.from(content.toString('utf8').trim(), 'base64');
            }
            return content;
        }

        throw new Error(`License not found. Please set GRAVITEE_LICENSE or provide a license file at ${this.localLicensePath}`);
    }

    async getLicenseBase64() {
        const content = await this.getLicenseContent();
        return Buffer.from(content).toString('base64');
    }
}
