import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import { describe, expect, it } from 'vitest';
import { formatDateRange, fromApi, isValid, toApi } from '../datetime';

dayjs.extend(utc);

/**
 * The UTC round trip, which is the one place a quiet bug would move a mission by hours.
 *
 * The assertions are written so they hold in any timezone the suite happens to run in - comparing
 * instants rather than rendered strings - because pinning the test to one offset would make it
 * pass on this machine and fail in CI.
 */
describe('datetime', () => {
  it('parses an API instant without moving it', () => {
    const parsed = fromApi('2026-09-01T08:00:00Z');

    expect(parsed.toISOString()).toBe('2026-09-01T08:00:00.000Z');
  });

  it('returns null for an absent value', () => {
    expect(fromApi(null)).toBeNull();
    expect(fromApi(undefined)).toBeNull();
  });

  it('sends back the same instant it was given', () => {
    // Local time in, UTC out, and nothing shifted along the way.
    expect(toApi(fromApi('2026-09-01T08:00:00Z'))).toBe('2026-09-01T08:00:00.000Z');
  });

  it('truncates seconds, because the picker only offers minutes', () => {
    const withSeconds = dayjs.utc('2026-09-01T08:00:37.500Z').local();

    expect(toApi(withSeconds)).toBe('2026-09-01T08:00:00.000Z');
  });

  it('survives a full round trip through local time', () => {
    const original = '2027-01-15T23:45:00Z';

    expect(toApi(fromApi(original))).toBe('2027-01-15T23:45:00.000Z');
  });

  describe('isValid', () => {
    it('rejects null and unparseable values', () => {
      expect(isValid(null)).toBe(false);
      expect(isValid(dayjs('not a date'))).toBe(false);
      expect(isValid(dayjs('2026-09-01'))).toBe(true);
    });
  });

  describe('formatDateRange', () => {
    it('drops the repeated year when both ends share one', () => {
      const range = formatDateRange('2026-09-01T08:00:00Z', '2026-09-14T17:00:00Z');

      expect(range).toContain('2026');
      // The year appears once, on the end date, rather than on both.
      expect(range.match(/2026/g)).toHaveLength(1);
    });

    it('keeps both years when the mission spans a new year', () => {
      const range = formatDateRange('2026-12-20T08:00:00Z', '2027-01-10T17:00:00Z');

      expect(range).toContain('2026');
      expect(range).toContain('2027');
    });

    it('shows times rather than two identical dates for a single-day mission', () => {
      const range = formatDateRange('2026-09-01T08:00:00Z', '2026-09-01T17:00:00Z');

      expect(range).toMatch(/\d{2}:\d{2} - \d{2}:\d{2}/);
    });
  });
});
