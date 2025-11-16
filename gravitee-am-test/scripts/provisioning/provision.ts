import { provision, purge } from '../provision';

if (require.main === module) {
  (async () => {
    const args = process.argv.slice(2);
    const isPurge = args.includes('--purge');
    const verify = args.includes('--verify');
    try {
      if (isPurge) {
        const prefixArgIndex = args.findIndex((a) => a === '--prefix');
        const prefix = prefixArgIndex >= 0 && args[prefixArgIndex + 1] ? args[prefixArgIndex + 1] : 'prov';
        await purge(prefix, verify);
      } else {
        const configArg = args[0];
        if (!configArg) {
          console.error('Usage: npm run provision -- <config.json>');
          process.exit(1);
        }
        await provision(configArg, verify);
      }
      process.exit(0);
    } catch (err) {
      console.error('Provisioning failed:', err);
      process.exit(1);
    }
  })();
}

