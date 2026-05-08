import { AbstractStepRelease } from './abstract-step-release.mjs';

export class ReleaseGammaVersion extends AbstractStepRelease {
    buildCommand(program) {
        super.buildCommand(program, 'release-gamma-version');
        this.command
                .description('trigger pipeline release gamma version on CircleCI (Artifactory only, no Docker Hub / Maven Central)')
                .usage('npm run am-release -- --branch="gamma" --gio-version="4.12.0-gamma.1" --dry-run release-gamma-version')
                .option('--create-maint-branch', 'creates maintenance version branch - false by default', false);
    }

    computePipelineParameters(options) {
        return {
            dry_run: options.dryRun,
            graviteeio_version: options.gioVersion,
            gio_action: 'release-gamma-version',
            rc_requested: true,
            tag_latest: false,
            tag_latest_support: false,
            create_maintenance_version_branch: options.createMaintBranch
        };
    }
}
