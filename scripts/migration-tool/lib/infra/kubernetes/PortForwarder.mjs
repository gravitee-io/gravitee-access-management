/**
 * PortForwarder manages K8s port-forwarding tunnels.
 */
export class PortForwarder {
    constructor(options) {
        this.namespace = options.namespace;
        this.shell = options.shell || $;
        this.spawn = options.spawn; // Allowed to be injected for tests
    }

    /**
     * Starts a port-forward in the background.
     * @returns {number} The PID of the background process.
     */
    async start(resource, localPort, remotePort) {
        console.log(`🔌 Starting port-forward: ${resource} ${localPort}:${remotePort}...`);

        let spawnFn = this.spawn;
        if (!spawnFn) {
            const cp = await import('node:child_process');
            spawnFn = cp.spawn;
        }

        // Use native spawn with detached: true and stdio: 'ignore' 
        // to ensure the process survives when the parent exits.
        const child = spawnFn('kubectl', [
            'port-forward',
            '-n', this.namespace,
            resource,
            '--address', '127.0.0.1,::1',
            `${localPort}:${remotePort}`
        ], {
            detached: true,
            stdio: 'ignore'
        });

        if (!child.pid) {
            throw new Error(`Failed to start port-forward for ${resource}`);
        }

        // Allow the parent to exit independently of this child
        child.unref();

        // Give it a moment to initialize and bind
        await new Promise(resolve => setTimeout(resolve, 1500));

        return child.pid;
    }

    async stop(pid) {
        if (!pid) return;
        console.log(`🔌 Stopping port-forward (PID: ${pid})...`);
        try {
            await this.shell`kill ${pid}`.quiet().nothrow();
        } catch (e) {
            // Process might already be dead
        }
    }

    /**
     * Forcefully kills any process listening on a specific port.
     */
    async forceKillPort(port) {
        try {
            // Find PIDs using lsof
            const result = await this.shell`lsof -ti :${port}`.quiet().nothrow();
            if (result.stdout) {
                const pids = result.stdout.trim().split('\n').filter(p => p);
                for (const pid of pids) {
                    console.log(`🔌 Killing orphaned process on port ${port} (PID: ${pid})...`);
                    await this.shell`kill -9 ${pid}`.quiet().nothrow();
                }
            }
        } catch (e) {
            // No process found or kill failed
        }
    }
}
