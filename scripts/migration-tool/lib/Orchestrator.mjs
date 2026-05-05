import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import process from 'node:process';
import { $, cd, within } from 'zx';

/**
 * Orchestrator manages the migration test lifecycle and stages.
 * testDir and test env come from options and provider; it has no knowledge of a specific test suite.
 */
export class Orchestrator {
    constructor(provider, options) {
        this.provider = provider;
        this.options = options;
        this.projectRoot = process.cwd();
        this._spawn = spawn;
    }

    async run(stages, options = {}) {
        const { skipCleanup = false } = options;
        console.log(`🛠️ Running Migration Orchestration`);
        console.log(`📝 Tags: ${this.options.fromTag} -> ${this.options.toTag}`);

        this.results = stages.map(stage => ({ stage, status: 'skipped', durationMs: 0 }));
        const results = this.results;
        let failedError = null;

        try {
            for (let i = 0; i < stages.length; i++) {
                const stage = stages[i];
                console.log(`\n▶️ Stage: ${stage}`);
                const start = Date.now();
                try {
                    this._currentStage = stage;
                    await this.executeStage(stage);
                    results[i].status = 'passed';
                    results[i].durationMs = Date.now() - start;
                    console.log(`  ✓ ${stage} (${this._formatDuration(results[i].durationMs)})`);
                } catch (error) {
                    results[i].status = 'failed';
                    results[i].durationMs = Date.now() - start;
                    results[i].error = error.message;
                    console.error(`\n❌ ${stage} failed: ${error.message}`);
                    if (error.stderr) console.error(`Stderr: ${error.stderr}`);
                    failedError = error;
                    break;
                }
            }
        } finally {
            this._printSummary(results);
            this._writeSummaryHtml(results);
            if (!skipCleanup && typeof this.provider.cleanup === 'function') {
                await this.provider.cleanup();
            }
        }

        if (failedError) throw failedError;
        return results;
    }

    _formatDuration(ms) {
        if (ms < 1000) return `${ms}ms`;
        return `${(ms / 1000).toFixed(1)}s`;
    }

    _printSummary(results) {
        const maxStageLen = Math.max(...results.map(r => r.stage.length), 5);
        const header = `${'Stage'.padEnd(maxStageLen)}  Status     Duration`;
        const separator = '─'.repeat(header.length);

        console.log(`\n${separator}`);
        console.log(header);
        console.log(separator);
        for (const r of results) {
            const icon = r.status === 'passed' ? '✓' : r.status === 'failed' ? '✗' : '-';
            const status = `${icon} ${r.status}`.padEnd(10);
            const duration = r.status === 'skipped' ? '-' : this._formatDuration(r.durationMs);
            console.log(`${r.stage.padEnd(maxStageLen)}  ${status} ${duration}`);
        }
        console.log(separator);

        const failed = results.find(r => r.status === 'failed');
        if (failed) {
            console.log(`Result: FAILED at ${failed.stage}`);
        } else if (results.every(r => r.status === 'passed')) {
            console.log('Result: ALL PASSED');
        }
        console.log('');
    }

    _escapeHtml(s) {
        return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    _writeSummaryHtml(results) {
        const testDir = this.options.testDir;
        if (!testDir) return;

        const htmlDir = join(testDir, 'jest-reports', 'html');
        try { mkdirSync(htmlDir, { recursive: true }); } catch (_) { /* ignore */ }

        const totalMs = results.reduce((sum, r) => sum + r.durationMs, 0);
        const failed = results.find(r => r.status === 'failed');
        const overallStatus = failed ? 'FAILED' : 'PASSED';
        const overallColor = failed ? '#e74c3c' : '#27ae60';

        const statusColor = (s) => s === 'passed' ? '#27ae60' : s === 'failed' ? '#e74c3c' : '#95a5a6';
        const verifyStages = results.filter(r => r.stage.startsWith('verify'));

        const nonSkipped = results.filter(r => r.status !== 'skipped');
        const minPct = 2;
        const rawPcts = nonSkipped.map(r => totalMs > 0 ? (r.durationMs / totalMs) * 100 : 100 / Math.max(nonSkipped.length, 1));
        const adjusted = rawPcts.map(p => Math.max(p, minPct));
        const totalAdj = adjusted.reduce((a, b) => a + b, 0);
        const normalised = adjusted.map(p => (p / totalAdj) * 100);

        const timelineSegments = nonSkipped
            .map((r, i) => {
                const pct = normalised[i];
                const label = this._escapeHtml(r.stage);
                const title = this._escapeHtml(`${r.stage}: ${this._formatDuration(r.durationMs)}`);
                return `<div style="width:${pct}%;background:${statusColor(r.status)};height:32px;display:inline-block;position:relative;cursor:pointer" title="${title}"><span style="position:absolute;left:4px;top:6px;font-size:11px;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:calc(100% - 8px)">${label}</span></div>`;
            }).join('');

        const stageRows = results.map(r => {
            const icon = r.status === 'passed' ? '&#10003;' : r.status === 'failed' ? '&#10007;' : '&#8211;';
            const duration = r.status === 'skipped' ? '-' : this._formatDuration(r.durationMs);
            const reportLink = r.stage.startsWith('verify') ? `<a href="report-${this._escapeHtml(r.stage)}.html">View Report</a>` : '';
            return `<tr style="border-bottom:1px solid #eee"><td style="padding:8px 12px">${this._escapeHtml(r.stage)}</td><td style="padding:8px 12px;color:${statusColor(r.status)};font-weight:bold">${icon} ${r.status}</td><td style="padding:8px 12px">${duration}</td><td style="padding:8px 12px">${this._escapeHtml(r.error || '')}</td><td style="padding:8px 12px">${reportLink}</td></tr>`;
        }).join('\n');

        const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Migration Test Summary</title>
<style>
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 24px; background: #f8f9fa; }
  .container { max-width: 960px; margin: 0 auto; }
  h1 { margin: 0 0 8px; font-size: 24px; }
  .subtitle { color: #666; margin: 0 0 24px; font-size: 14px; }
  .status-badge { display: inline-block; padding: 4px 16px; border-radius: 4px; color: #fff; font-weight: bold; font-size: 14px; }
  .timeline { display: flex; border-radius: 6px; overflow: hidden; margin: 24px 0; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
  table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
  th { background: #f1f3f5; padding: 10px 12px; text-align: left; font-size: 13px; color: #495057; }
  .reports { margin-top: 24px; }
  .reports a { display: inline-block; margin: 4px 8px 4px 0; padding: 6px 14px; background: #fff; border: 1px solid #dee2e6; border-radius: 4px; text-decoration: none; color: #495057; font-size: 13px; }
  .reports a:hover { background: #e9ecef; }
</style>
</head>
<body>
<div class="container">
  <h1>Migration Test Summary</h1>
  <p class="subtitle">${this._escapeHtml(this.options.fromTag)} &rarr; ${this._escapeHtml(this.options.toTag)} &nbsp; | &nbsp; Total: ${this._formatDuration(totalMs)} &nbsp; | &nbsp; <span class="status-badge" style="background:${overallColor}">${overallStatus}</span></p>
  <div class="timeline">${timelineSegments}</div>
  <table>
    <thead><tr><th>Stage</th><th>Status</th><th>Duration</th><th>Error</th><th>Report</th></tr></thead>
    <tbody>${stageRows}</tbody>
  </table>
  ${verifyStages.length > 0 ? `<div class="reports"><h3>Test Reports</h3>${verifyStages.map(r => `<a href="report-${r.stage}.html">${this._escapeHtml(r.stage)}</a>`).join('')}</div>` : ''}
</div>
</body>
</html>`;

        try {
            writeFileSync(join(htmlDir, 'migration-summary.html'), html);
            console.log(`📊 Summary report: ${join(htmlDir, 'migration-summary.html')}`);
        } catch (e) {
            console.warn(`⚠️ Could not write summary HTML: ${e.message}`);
        }
    }

    async executeStage(stage) {
        switch (stage) {
            case 'clean':
                await this.provider.clean();
                break;
            case 'k8s:setup':
                if (typeof this.provider.setup === 'function') {
                    await this.provider.setup();
                } else {
                    console.log('⏭️ Skipping setup for current provider.');
                }
                break;
            case 'deploy-from':
                await this.provider.deploy(this.options.fromTag);
                break;
            case 'seed':
                await this.runSeed(this.options.fromVersion);
                break;
            case 'seed-upgrade':
                await this.runSeed(this.options.toVersion, { fromVersion: this.options.fromVersion });
                break;
            case 'verify-baseline':
                await this.runTests('🔍 Running baseline tests...', 'ci:migration', 'specs/migration');
                break;
            case 'upgrade-mapi':
                await this.provider.upgradeMapi(this.options.toTag);
                break;
            case 'verify-mapi':
                await this.runTests('🔍 Verifying MAPI upgrade...', 'ci:migration', 'specs/migration');
                break;
            case 'upgrade-gw':
                await this.provider.upgradeGw(this.options.toTag);
                break;
            case 'verify-all':
                await this.runTests('🔍 Final verification...', 'ci:migration', 'specs/migration');
                break;
            case 'downgrade-mapi':
                await this.provider.upgradeMapi(this.options.fromTag);
                break;
            case 'verify-after-downgrade-mapi':
                await this.runTests('🔍 Verifying after MAPI downgrade...', 'ci:migration', 'specs/migration');
                break;
            case 'downgrade-gw':
                await this.provider.upgradeGw(this.options.fromTag);
                break;
            case 'verify-after-downgrade':
                await this.runTests('🔍 Verifying after downgrade...', 'ci:migration', 'specs/migration');
                break;
            default:
                throw new Error(`Unknown stage: ${stage}`);
        }
    }

    async runTests(message, npmScript, specPath) {
        console.log(message);
        const testDir = this.options.testDir;
        if (!testDir) {
            throw new Error('options.testDir is required to run tests');
        }
        const filter = (this.options.testFilter || '').trim();

        const stageName = this._currentStage || 'unknown';
        const jestEnv = {
            ...process.env,
            ...(typeof this.provider.getTestEnv === 'function' ? this.provider.getTestEnv() : {}),
            JEST_JUNIT_OUTPUT_NAME: `junit-migration-${stageName}.xml`,
            JEST_HTML_REPORT_NAME: `report-${stageName}.html`
        };
        Object.assign(process.env, jestEnv);

        if (typeof this.provider.prepareTests === 'function') {
            await this.provider.prepareTests();
        }

        await within(async () => {
            cd(testDir);

            if (filter) {
                console.log(`🔍 Filtering tests by: ${filter}`);
                const args = ['jest', '--no-cache', '--config=api/config/ci.config.js', `--testPathPattern=${filter}`];
                await this._runJestWithEnv(testDir, jestEnv, args);
            } else {
                await $`npm run ${npmScript} -- ${specPath || ''}`;
            }
        });
    }

    async _runJestWithEnv(testDir, env, args) {
        const child = spawn('npx', args, {
            cwd: testDir,
            env: { ...process.env, ...env },
            stdio: 'inherit',
            shell: false,
        });
        const [code] = await once(child, 'exit');
        if (code !== 0) {
            throw new Error(`Jest exited with code ${code}`);
        }
    }

    async runSeed(version, { fromVersion } = {}) {
        console.log(`🌱 Seeding test data for version ${version}${fromVersion ? ` (from ${fromVersion})` : ''}...`);
        const testDir = this.options.testDir;
        if (!testDir) {
            throw new Error('options.testDir is required to run seed');
        }
        const env = {
            ...process.env,
            ...(typeof this.provider.getTestEnv === 'function' ? this.provider.getTestEnv() : {})
        };
        const args = ['run', 'migration:seed', '--', '--to-version', version];
        if (fromVersion) {
            args.push('--from-version', fromVersion);
        }
        const child = this._spawn('npm', args, {
            cwd: testDir,
            env,
            stdio: 'inherit',
            shell: false,
        });
        const [code] = await once(child, 'exit');
        if (code !== 0) {
            throw new Error(`Seed process exited with code ${code}`);
        }
    }
}
