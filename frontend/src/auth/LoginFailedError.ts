/**
 * Why a sign-in attempt did not succeed, in words the form can show.
 *
 * Its own module so that `AuthProvider.tsx` exports nothing but a component - otherwise
 * `react-refresh/only-export-components` warns, and hot reload of the provider stops working.
 *
 * The backend deliberately returns one error for an unknown email and for a wrong password, so
 * there is nothing here that tells them apart either.
 */
export class LoginFailedError extends Error {
  readonly problemType: string | undefined;

  constructor(message: string, problemType?: string) {
    super(message);
    this.name = 'LoginFailedError';
    this.problemType = problemType;
  }
}
