export async function runWithConcurrency<I, O>(items: I[], limit: number, worker: (item: I, index: number) => Promise<O>): Promise<O[]> {
  const results: O[] = new Array(items.length) as O[];
  let idx = 0;
  async function next(): Promise<void> {
    const current = idx++;
    if (current >= items.length) return;
    results[current] = await worker(items[current], current);
    return next();
  }
  const starters = Array.from({ length: Math.min(limit, items.length) }, () => next());
  await Promise.all(starters);
  return results;
}


