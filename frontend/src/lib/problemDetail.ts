import type { ProblemDetail } from '../api/generated/types.gen';

/**
 * Turning an RFC 9457 problem into a sentence somebody can act on.
 *
 * Two things make this necessary rather than just reading `detail`. First, the generated client
 * types the error channel of a query as `ProblemDetail`, which has no `message` - so the obvious
 * `error.message` is `undefined` at runtime. Second, two of the mission errors carry structured
 * properties that say far more than the sentence does: `mission-understaffed` knows exactly which
 * requirements are short, and repeating that as "2 crew requirements are not yet filled" throws
 * away the part the reader needs.
 *
 * A plain module rather than a hook, and a `.ts` file rather than living beside a component,
 * because `react-refresh/only-export-components` warns as soon as a `.tsx` file exports anything
 * that is not a component.
 */

/** The shape `mission-understaffed` adds to the problem body. */
interface Shortfall {
  id: string;
  title: string;
  requiredCount: number;
  acceptedCount: number;
}

const FALLBACK = 'Something went wrong. Please try again.';

/**
 * The sentence to show for a failed request.
 *
 * Accepts `unknown` because that is what a thrown query error is typed as, and because the value
 * may be a network `Error` rather than a problem body at all.
 */
export function messageForProblem(error: unknown, fallback = FALLBACK): string {
  const problem = asProblemDetail(error);

  if (!problem) {
    return error instanceof Error && error.message ? error.message : fallback;
  }

  switch (problem.type) {
    case 'urn:mission-control:mission-understaffed':
      return understaffedMessage(problem);

    case 'urn:mission-control:invalid-transition':
      return problem.detail ?? 'That is not something this mission can do right now.';

    case 'urn:mission-control:mission-has-no-requirements':
      return 'Add at least one crew requirement before submitting this mission for approval.';

    case 'urn:mission-control:mission-not-editable':
      return (
        problem.detail ??
        'Crew requirements can only be changed while the mission is still being planned.'
      );

    case 'urn:mission-control:duplicate-skill':
      return 'Each skill can only be listed once on a requirement.';

    case 'urn:mission-control:invalid-skill':
      return 'One of the chosen skills is no longer available. Pick another.';

    case 'urn:mission-control:validation-failed':
      return fieldErrors(problem) ?? problem.detail ?? 'Some of the details are not valid.';

    case 'urn:mission-control:forbidden':
      return problem.detail ?? 'You do not have permission to do that.';

    case 'urn:mission-control:not-found':
      return 'This mission does not exist, or you do not have access to it.';

    default:
      return problem.detail ?? fallback;
  }
}

/**
 * The requirements a mission is short of, if the error carried them.
 *
 * Returned separately so a screen can render them as a list rather than a run-on sentence.
 */
export function shortfallsFrom(error: unknown): Shortfall[] {
  const problem = asProblemDetail(error);
  if (problem?.type !== 'urn:mission-control:mission-understaffed') {
    return [];
  }
  const requirements = (problem as Record<string, unknown>).requirements;
  return Array.isArray(requirements) ? (requirements as Shortfall[]) : [];
}

function understaffedMessage(problem: ProblemDetail): string {
  const shortfalls = shortfallsFrom(problem);
  if (shortfalls.length === 0) {
    return problem.detail ?? 'This mission is not fully crewed yet.';
  }
  const listed = shortfalls
    .map((s) => `${s.title} (${s.acceptedCount} of ${s.requiredCount})`)
    .join(', ');
  return `Not fully crewed yet: ${listed}.`;
}

/**
 * Bean-validation failures arrive as a map of field to message. One field is worth naming; several
 * would be a wall of text, so those fall back to the generic sentence.
 */
function fieldErrors(problem: ProblemDetail): string | null {
  const errors = (problem as Record<string, unknown>).errors;
  if (!errors || typeof errors !== 'object') return null;

  const entries = Object.entries(errors as Record<string, string>);
  if (entries.length !== 1) return null;

  const [field, message] = entries[0]!;
  return `${humanise(field)} ${message}.`;
}

function humanise(field: string): string {
  const spaced = field.replace(/([A-Z])/g, ' $1').toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

/**
 * Recognises a problem body.
 *
 * The generated client hands back the parsed body directly on the imperative path and a thrown
 * value on the query path, so this has to cope with either. A `type` starting with the project
 * URN prefix is the reliable marker - `status` alone is not, since a plain `Response` has one too.
 */
function asProblemDetail(error: unknown): ProblemDetail | null {
  if (!error || typeof error !== 'object') return null;

  const candidate = error as ProblemDetail;
  if (typeof candidate.type === 'string' && candidate.type.startsWith('urn:mission-control:')) {
    return candidate;
  }
  return null;
}
