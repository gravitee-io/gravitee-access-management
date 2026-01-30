import { LicenseManager } from '../../lib/infra/kubernetes/LicenseManager.mjs';
import fs from 'fs';
import { jest } from '@jest/globals';

/**
 * TDD for LicenseManager
 */
describe('LicenseManager', () => {
    let licenseManager;
    let mockKubectl;

    beforeEach(() => {
        mockKubectl = {
            createSecretFromFile: jest.fn(),
            secretExists: jest.fn()
        };
        licenseManager = new LicenseManager({
            namespace: 'gravitee-am',
            kubectl: mockKubectl,
            localLicensePath: '/tmp/license.key'
        });
    });

    test('should find license from environment variable in CI', async () => {
        process.env.GRAVITEE_LICENSE = 'ci-license-content';
        const license = await licenseManager.getLicenseContent();
        expect(license).toBe('ci-license-content');
        delete process.env.GRAVITEE_LICENSE;
    });

    test('should find license from local file if env is missing', async () => {
        const spy = jest.spyOn(fs, 'readFileSync').mockReturnValue('local-license-content');
        const spyExist = jest.spyOn(fs, 'existsSync').mockReturnValue(true);

        const license = await licenseManager.getLicenseContent();

        expect(license).toBe('local-license-content');
        spy.mockRestore();
        spyExist.mockRestore();
    });

    test('should throw error if license is missing everywhere', async () => {
        jest.spyOn(fs, 'existsSync').mockReturnValue(false);
        await expect(licenseManager.getLicenseContent()).rejects.toThrow('License not found');
    });

    test('should return license content as base64', async () => {
        const rawContent = 'test-license-content';
        const spyExist = jest.spyOn(fs, 'existsSync').mockReturnValue(true);
        const spyRead = jest.spyOn(fs, 'readFileSync').mockReturnValue(Buffer.from(rawContent));

        const licenseBase64 = await licenseManager.getLicenseBase64();

        expect(licenseBase64).toBe(Buffer.from(rawContent).toString('base64'));
        spyExist.mockRestore();
        spyRead.mockRestore();
    });

    test('should handle base64 source and return base64', async () => {
        const rawContent = 'test-license-content';
        const b64 = Buffer.from(rawContent).toString('base64');
        process.env.GRAVITEE_LICENSE = b64;

        const licenseBase64 = await licenseManager.getLicenseBase64();

        expect(licenseBase64).toBe(b64);
        delete process.env.GRAVITEE_LICENSE;
    });

    test('should decode base64 license from .b64 file', async () => {
        delete process.env.GRAVITEE_LICENSE;
        const rawContent = 'plain-text-license';
        const b64Content = Buffer.from(rawContent).toString('base64');

        licenseManager.localLicensePath = 'test-license.b64';
        const spyExists = jest.spyOn(fs, 'existsSync').mockReturnValue(true);
        const spyRead = jest.spyOn(fs, 'readFileSync').mockReturnValue(Buffer.from(b64Content));

        const license = await licenseManager.getLicenseContent();

        expect(license.toString()).toBe(rawContent);
        spyExists.mockRestore();
        spyRead.mockRestore();
    });
});
