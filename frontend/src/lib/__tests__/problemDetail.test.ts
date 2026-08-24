import { describe, expect, it } from 'vitest';
import { messageForProblem, shortfallsFrom } from '../problemDetail';

/**
 * Turning the API's problem bodies into sentences.
 *
 * Worth covering carefully because the failure is silent: an unrecognised shape produces
 * `undefined` rather than an error, and the user sees an empty red box instead of a reason.
 */
describe('messageForProblem', () => {
  it('lists the requirements that are short when a mission cannot start', () => {
    const message = messageForProblem({
      type: 'urn:mission-control:mission-understaffed',
      detail: '2 crew requirements are not yet filled.',
      requirements: [
        { id: '1', title: 'Flight Engineer', requiredCount: 2, acceptedCount: 0 },
        { id: '2', title: 'Navigator', requiredCount: 1, acceptedCount: 0 },
      ],
    });

    // The names are the part the reader needs; the count alone would send them hunting.
    expect(message).toContain('Flight Engineer (0 of 2)');
    expect(message).toContain('Navigator (0 of 1)');
  });

  it('falls back to the detail when understaffing carries no requirements', () => {
    const message = messageForProblem({
      type: 'urn:mission-control:mission-understaffed',
      detail: 'This mission has no crew requirements, so there is nobody to fly it.',
    });

    expect(message).toBe('This mission has no crew requirements, so there is nobody to fly it.');
  });

  it('uses the server sentence for an invalid transition, which names both statuses', () => {
    const message = messageForProblem({
      type: 'urn:mission-control:invalid-transition',
      detail: 'A mission in PLAN cannot move to ACTIVE.',
      currentStatus: 'PLAN',
      attemptedTransition: 'ACTIVE',
    });

    expect(message).toBe('A mission in PLAN cannot move to ACTIVE.');
  });

  it('explains a duplicate skill in its own words', () => {
    expect(messageForProblem({ type: 'urn:mission-control:duplicate-skill' })).toBe(
      'Each skill can only be listed once on a requirement.',
    );
  });

  it('explains an unusable skill without leaking whether it exists elsewhere', () => {
    const message = messageForProblem({ type: 'urn:mission-control:invalid-skill' });

    expect(message).toBe('One of the chosen skills is no longer available. Pick another.');
  });

  it('names the single field that failed validation', () => {
    const message = messageForProblem({
      type: 'urn:mission-control:validation-failed',
      detail: 'One or more fields are invalid.',
      errors: { requiredCount: 'must be at least 1' },
    });

    expect(message).toBe('Required count must be at least 1.');
  });

  it('falls back to the generic sentence when several fields failed', () => {
    const message = messageForProblem({
      type: 'urn:mission-control:validation-failed',
      detail: 'One or more fields are invalid.',
      errors: { startsAt: 'must not be null', endsAt: 'must not be null' },
    });

    expect(message).toBe('One or more fields are invalid.');
  });

  it('does not reveal whether a mission exists when it cannot be seen', () => {
    const message = messageForProblem({ type: 'urn:mission-control:not-found' });

    expect(message).toBe('That mission no longer exists, or you no longer have access to it.');
  });

  it('reads the message off a plain Error, which is what a network failure looks like', () => {
    expect(messageForProblem(new Error('Failed to fetch'))).toBe('Failed to fetch');
  });

  it('uses the supplied fallback for anything unrecognisable', () => {
    expect(messageForProblem(undefined, 'Could not load these missions.')).toBe(
      'Could not load these missions.',
    );
    expect(messageForProblem({ status: 500 }, 'Could not load these missions.')).toBe(
      'Could not load these missions.',
    );
  });

  it('ignores a type from somewhere that is not this application', () => {
    // A `status` field alone is not enough to treat something as a problem body.
    expect(messageForProblem({ type: 'about:blank', status: 500 }, 'fallback')).toBe('fallback');
  });
});

describe('shortfallsFrom', () => {
  it('returns the requirements for an understaffed error', () => {
    const shortfalls = shortfallsFrom({
      type: 'urn:mission-control:mission-understaffed',
      requirements: [{ id: '1', title: 'Navigator', requiredCount: 1, acceptedCount: 0 }],
    });

    expect(shortfalls).toHaveLength(1);
    expect(shortfalls[0]!.title).toBe('Navigator');
  });

  it('returns nothing for any other error', () => {
    expect(shortfallsFrom({ type: 'urn:mission-control:not-found' })).toEqual([]);
    expect(shortfallsFrom(new Error('boom'))).toEqual([]);
  });
});
