import { beforeEach, describe, expect, it } from 'vitest';
import { clearSession, readSession, writeSession } from '../tokenStorage';

const KEY = 'mc.auth';

function inHours(hours: number): string {
  return new Date(Date.now() + hours * 3_600_000).toISOString();
}

describe('tokenStorage', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('round-trips a session', () => {
    const session = { token: 'abc.def.ghi', expiresAt: inHours(8) };

    writeSession(session);

    expect(readSession()).toEqual(session);
  });

  it('returns null when nothing is stored', () => {
    expect(readSession()).toBeNull();
  });

  it('returns null and cleans up when the stored value is not JSON', () => {
    window.localStorage.setItem(KEY, 'not json at all');

    expect(readSession()).toBeNull();
    expect(window.localStorage.getItem(KEY)).toBeNull();
  });

  it('returns null when the stored value is missing fields', () => {
    window.localStorage.setItem(KEY, JSON.stringify({ token: 'abc' }));

    expect(readSession()).toBeNull();
  });

  it('discards an already-expired session rather than attempting to use it', () => {
    writeSession({ token: 'stale', expiresAt: inHours(-1) });

    expect(readSession()).toBeNull();
    expect(window.localStorage.getItem(KEY)).toBeNull();
  });

  it('clears the session', () => {
    writeSession({ token: 'abc', expiresAt: inHours(8) });

    clearSession();

    expect(readSession()).toBeNull();
  });
});
