import { AbstractStepRelease } from './abstract-step-release.mjs';

export class ReleaseVersion extends AbstractStepRelease {
    buildCommand(program) {
        super.buildCommand(program, 'release-version');
        this.command
                .description('trigger pipeline release version on CircleCI')
                .usage('npm run am-release -- --branch="4.3.x" --gio-version="4.3.2" --dry-run release-version')
                .option('-rc, --release-candidate', 'this is a release candidate', false)
                .option('--tag-latest', 'this is the latest version of the product - false by default', false)
                .option(
                        '--tag-latest-support, --no-tag-latest-support',
                        'this is the latest support version of the product (eg: 3.20 == 3.20.6) - by default it is (true)',
                        true,
                );
    }

    computePipelineParameters(options) {
        return {
            dry_run: options.dryRun,
            graviteeio_version: options.gioVersion,
            gio_action: 'release-version',
            rc_requested: options.releaseCandidate,
            tag_latest: options.tagLatest,
            tag_latest_support: options.tagLatestSupport,
            create_maintenance_version_branch: true
        };
    }
}
