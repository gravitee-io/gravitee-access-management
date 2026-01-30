import fs from 'fs';
import path from 'path';
import os from 'os';

/**
 * LicenseManager handles Gravitee license detection and injection into K8s.
 */
export class LicenseManager {
    constructor(options) {
        this.namespace = options.namespace;
        this.kubectl = options.kubectl;
        this.localLicensePath = options.localLicensePath;
        this.tempLicenseFile = path.join(os.tmpdir(), `am-license-${Date.now()}.key`);
    }

    async getLicenseContent() {
        // 1. Check CI environment variable
        if (process.env.GRAVITEE_LICENSE) {
            const content = process.env.GRAVITEE_LICENSE.trim();
            // If it looks like base64, decode it to be sure it's binary, then return as string
            if (/^[A-Za-z0-9+/=]+$/.test(content) && content.length > 20) {
                return Buffer.from(content, 'base64');
            }
            return content;
        }

        // 2. Check Local File
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
        // content is now always a string (from getLicenseContent refactor)
        // If it was originally binary, it might have been mangled by toString('utf8')
        // Actually, let's fix getLicenseContent to return Buffer when it's binary.
        return Buffer.from(content).toString('base64');
    }

    async injectLicense() {
        // This is now legacy as we'll pass base64 to Helm directly,
        // but keeping it for compatibility if needed.
        console.log('🗝️  Preparing Gravitee license...');
        // ... (rest of the logic or just a warning that it's moved to Helm)
        console.log('ℹ️  Note: License injection is now handled via Helm --set.');
    }
}
