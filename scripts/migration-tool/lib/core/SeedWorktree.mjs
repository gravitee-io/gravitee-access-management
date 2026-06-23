import { existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

/**
 * SeedWorktree materializes a git worktree for a given ref (tag/branch/commit) so the migration
 * seed step can run that version's *own* seed tooling — its committed Management API SDK and
 * migration-seeding scripts — rather than the current checkout's.
 *
 * The AM instance itself still runs from the published Docker image for the ref; only the seed
 * tooling comes from the worktree. Worktrees are cached under <repoRoot>/.worktrees and `npm ci`
 * runs once per worktree (skipped when node_modules already exists).
 *
 * Going-forward contract: refs that predate the migration-seeding framework (no migration-seeding/
 * directory) are NOT used — resolveSeedDir() returns null so the caller falls back to the current
 * checkout. Relies on zx globals ($, cd, within) — only ever constructed under the zx-run CLI.
 */
export class SeedWorktree {
    constructor({ repoRoot, worktreeBase, testDirName = 'gravitee-am-test', keep = false } = {}) {
        this.repoRoot = repoRoot;
        this.worktreeBase = worktreeBase;
        this.testDirName = testDirName;
        this.keep = keep;
        this.created = [];
    }

    /** Build an instance rooted at the repository top-level. */
    static async create({ keep = false } = {}) {
        const repoRoot = (await $`git rev-parse --show-toplevel`).stdout.trim();
        return new SeedWorktree({ repoRoot, worktreeBase: join(repoRoot, '.worktrees'), keep });
    }

    sanitize(ref) {
        return ref.replace(/[^A-Za-z0-9._-]/g, '-');
    }

    /**
     * Materialize (or reuse) a worktree for `ref` and return the absolute gravitee-am-test directory
     * to seed from. Returns null when `ref` predates the migration-seeding framework, signalling the
     * caller to seed from the current checkout instead.
     */
    async resolveSeedDir(ref) {
        await this.ensureRef(ref);

        const worktreePath = join(this.worktreeBase, `seed-${this.sanitize(ref)}`);
        const justCreated = !existsSync(worktreePath);
        if (justCreated) {
            mkdirSync(this.worktreeBase, { recursive: true });
            console.log(`🌿 Creating seed worktree for ${ref} at ${worktreePath}`);
            await $`git -C ${this.repoRoot} worktree add --detach --force ${worktreePath} ${ref}`;
            this.created.push(worktreePath);
        }

        const seedDir = join(worktreePath, this.testDirName);
        if (!existsSync(join(seedDir, 'migration-seeding'))) {
            console.log(`ℹ️  Ref ${ref} predates the migration-seeding framework; seeding from current checkout instead.`);
            if (justCreated) {
                await this.removeWorktree(worktreePath);
            }
            return null;
        }

        if (!existsSync(join(seedDir, 'node_modules'))) {
            console.log(`📦 Installing seed dependencies for ${ref} (npm ci)...`);
            await within(async () => {
                cd(seedDir);
                await $`npm ci`;
            });
        }

        return seedDir;
    }

    /** Ensure `ref` resolves to a commit locally, fetching tags once if needed. */
    async ensureRef(ref) {
        const spec = `${ref}^{commit}`;
        if ((await $`git -C ${this.repoRoot} rev-parse --verify --quiet ${spec}`.quiet().nothrow()).exitCode === 0) {
            return;
        }
        await $`git -C ${this.repoRoot} fetch --tags --quiet`.quiet().nothrow();
        if ((await $`git -C ${this.repoRoot} rev-parse --verify --quiet ${spec}`.quiet().nothrow()).exitCode !== 0) {
            throw new Error(`Cannot resolve git ref '${ref}' for seeding worktree (not a tag/branch/commit, and fetch did not find it)`);
        }
    }

    async removeWorktree(worktreePath) {
        try {
            await $`git -C ${this.repoRoot} worktree remove --force ${worktreePath}`;
        } catch (error) {
            console.log(`⚠️  Could not remove seed worktree ${worktreePath}: ${error.message}`);
        }
        this.created = this.created.filter((p) => p !== worktreePath);
    }

    /** Remove every worktree this instance created, unless --keep-worktrees was requested. */
    async cleanup() {
        if (this.keep) {
            if (this.created.length) {
                console.log(`🧹 Keeping ${this.created.length} seed worktree(s) (--keep-worktrees).`);
            }
            return;
        }
        for (const worktreePath of [...this.created]) {
            await this.removeWorktree(worktreePath);
        }
    }
}
