/**
 * Read a JWT's payload without verifying it. Verification is the server's job (JwtService);
 * the client only needs `exp` to sign out on time.
 */
export function decodeJwt(token) {
  if (typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(b64 + '='.repeat((4 - (b64.length % 4)) % 4)), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}

/** Milliseconds until expiry; <= 0 when expired; Infinity without an exp claim. */
export function msUntilExpiry(token, now = Date.now()) {
  const claims = decodeJwt(token);
  if (!claims) return 0;
  if (typeof claims.exp !== 'number') return Infinity;
  return claims.exp * 1000 - now;
}
