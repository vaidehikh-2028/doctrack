import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Runs `fn(signal)` on mount and whenever `deps` change; aborts stale calls.
 * Returns { data, error, loading, reload }. `reload({silent:true})` refreshes without a spinner.
 */
export function useAsync(fn, deps) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const ctrlRef = useRef(null);
  const fnRef = useRef(fn);
  fnRef.current = fn;

  const run = useCallback(async ({ silent = false } = {}) => {
    ctrlRef.current?.abort();
    const ctrl = new AbortController();
    ctrlRef.current = ctrl;
    if (!silent) setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const data = await fnRef.current(ctrl.signal);
      if (!ctrl.signal.aborted) setState({ data, error: null, loading: false });
    } catch (error) {
      if (ctrl.signal.aborted || error.name === 'AbortError') return;
      setState((s) => ({ data: silent ? s.data : null, error, loading: false }));
    }
  }, []);

  useEffect(() => {
    run();
    return () => ctrlRef.current?.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  /** Replace data with a fresh server response (e.g. the RequestDetail an action returns). */
  const setData = useCallback((data) => {
    ctrlRef.current?.abort();
    setState({ data, error: null, loading: false });
  }, []);

  return { ...state, reload: run, setData };
}

/**
 * Calls `tick` every `ms` while the tab is visible, and once immediately when it becomes visible again.
 * Saves requests (and Render free-tier wakeups) when the user isn't looking.
 */
export function useVisiblePolling(tick, ms, enabled = true) {
  const tickRef = useRef(tick);
  tickRef.current = tick;

  useEffect(() => {
    if (!enabled) return undefined;
    let id = null;
    const start = () => {
      if (id == null) id = setInterval(() => tickRef.current(), ms);
    };
    const stop = () => {
      clearInterval(id);
      id = null;
    };
    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        tickRef.current();
        start();
      } else {
        stop();
      }
    };
    if (document.visibilityState === 'visible') start();
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      stop();
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [ms, enabled]);
}
