import { AbstractStepRelease } from './abstract-step-release.mjs';

export class ReleaseAlphaVersion extends AbstractStepRelease {
    buildCommand(program) {
        super.buildCommand(program, 'release-alpha-version');
        this.command
                .description('trigger pipeline release alpha version on CircleCI')
                .usage('npm run am-release -- --branch="4.3.x" --gio-version="4.3.2" --dry-run release-alpha-version')
                .option('--create-maint-branch', 'creates maintenance version branch - false by default', false);
    }

    computePipelineParameters(options) {
        return {
            dry_run: options.dryRun,
            graviteeio_version: options.gioVersion,
            gio_action: 'release-alpha-version',
            rc_requested: true,
            tag_latest: false,
            tag_latest_support: false,
            create_maintenance_version_branch: options.createMaintBranch
        };
    }
}
