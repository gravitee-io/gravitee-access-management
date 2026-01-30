/**
 * PortForwarder manages K8s port-forwarding tunnels.
 */
export class PortForwarder {
    constructor(options) {
        this.namespace = options.namespace;
        this.shell = options.shell || $;
    }

    /**
     * Starts a port-forward in the background.
     * @returns {number} The PID of the background process.
     */
    async start(resource, localPort, remotePort) {
        console.log(`🔌 Starting port-forward: ${resource} ${localPort}:${remotePort}...`);

        // Start as background process using zx $ (it runs immediately)
        const proc = this.shell`kubectl port-forward -n ${this.namespace} ${resource} ${localPort}:${remotePort}`.quiet();

        if (!proc.child || !proc.child.pid) {
            throw new Error(`Failed to start port-forward for ${resource}`);
        }

        // Detach background process to allow Node to exit while keeping tunnel alive
        proc.child.unref();

        return proc.child.pid;
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
