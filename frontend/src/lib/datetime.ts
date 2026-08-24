import dayjs, { type Dayjs } from 'dayjs';
import utc from 'dayjs/plugin/utc';

/**
 * The one place local time meets the UTC instants the API speaks.
 *
 * The backend stores and returns UTC throughout and the browser is the only thing that knows what
 * timezone the person reading is in, so every conversion belongs here rather than being repeated
 * at each call site with slightly different rules.
 *
 * `dayjs` with the utc plugin rather than raw `Date`, because `Date` has no way to say "this is an
 * instant, render it locally" without going through string parsing that varies by engine.
 */
dayjs.extend(utc);

/** Parses an ISO-8601 instant from the API into a local-time value the pickers can edit. */
export function fromApi(iso: string): Dayjs;
export function fromApi(iso: string | undefined | null): Dayjs | null;
export function fromApi(iso: string | undefined | null): Dayjs | null {
  return iso ? dayjs.utc(iso).local() : null;
}

/**
 * Converts a local-time value back to the UTC instant the API expects.
 *
 * Seconds are truncated deliberately: the pickers only offer minutes, so keeping whatever second
 * the value happened to be constructed with would make a mission start at an arbitrary 37 seconds
 * past.
 */
export function toApi(value: Dayjs): string {
  return value.second(0).millisecond(0).utc().toISOString();
}

/** A single instant, in the reader's own timezone. */
export function formatDateTime(iso: string): string {
  return fromApi(iso).format('D MMM YYYY, HH:mm');
}

/** Just the day, for places where the time of day is noise. */
export function formatDate(iso: string): string {
  return fromApi(iso).format('D MMM YYYY');
}

/**
 * A mission timeline as one phrase.
 *
 * The year is dropped from the start date when both ends fall in the same year, which is the
 * common case and reads far better on a card: '1 - 14 Sep 2026' rather than
 * '1 Sep 2026 - 14 Sep 2026'.
 */
export function formatDateRange(startsAtIso: string, endsAtIso: string): string {
  const start = fromApi(startsAtIso);
  const end = fromApi(endsAtIso);

  if (start.year() !== end.year()) {
    return `${start.format('D MMM YYYY')} - ${end.format('D MMM YYYY')}`;
  }
  if (start.month() === end.month() && start.date() === end.date()) {
    return `${start.format('D MMM YYYY')}, ${start.format('HH:mm')} - ${end.format('HH:mm')}`;
  }
  return `${start.format('D MMM')} - ${end.format('D MMM YYYY')}`;
}

/** True when the value is a real date the API would accept. */
export function isValid(value: Dayjs | null): value is Dayjs {
  return value !== null && value.isValid();
}
