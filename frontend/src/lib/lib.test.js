import { describe, expect, it } from 'vitest';
import { stepStates } from '../components/StatusStepper';
import { fakeJwt } from '../test/helpers';
import { relativeTime, validateFile } from './format';
import { decodeJwt, msUntilExpiry } from './jwt';

const file = (bytes, name, size) => {
  const f = new File([new Uint8Array(bytes)], name);
  if (size != null) Object.defineProperty(f, 'size', { value: size });
  return f;
};

describe('validateFile mirrors RequestValidator', () => {
  it('accepts a real PDF', async () => {
    expect(await validateFile(file([0x25, 0x50, 0x44, 0x46, 0x2d], 'a.pdf'))).toBeNull();
  });
  it('accepts PNG and JPEG by magic bytes', async () => {
    expect(await validateFile(file([0x89, 0x50, 0x4e, 0x47], 'a.png'))).toBeNull();
    expect(await validateFile(file([0xff, 0xd8, 0xff, 0xe0], 'a.JPG'))).toBeNull();
  });
  it('refuses a renamed executable', async () => {
    expect(await validateFile(file([0x4d, 0x5a, 0x90, 0x00], 'setup.pdf'))).toMatch(/not a real PDF/);
  });
  it('refuses other extensions', async () => {
    expect(await validateFile(file([0x4d, 0x5a], 'setup.exe'))).toMatch(/Only PDF, JPG or PNG/);
  });
  it('accepts exactly 5 MB and refuses 5 MB + 1 KB (TC-05 / TC-06)', async () => {
    const pdf = [0x25, 0x50, 0x44, 0x46];
    expect(await validateFile(file(pdf, 'a.pdf', 5 * 1024 * 1024))).toBeNull();
    expect(await validateFile(file(pdf, 'a.pdf', 5 * 1024 * 1024 + 1024))).toMatch(/up to 5 MB/);
  });
});

describe('jwt', () => {
  it('decodes claims and computes expiry', () => {
    const token = fakeJwt({ sub: '7', role: 'STAFF', exp: 2_000 });
    expect(decodeJwt(token)).toMatchObject({ sub: '7', role: 'STAFF' });
    expect(msUntilExpiry(token, 1_000_000)).toBe(1_000_000);
    expect(msUntilExpiry(token, 3_000_000)).toBeLessThan(0);
  });
  it('treats garbage as expired', () => {
    expect(decodeJwt('not.a.jwt')).toBeNull();
    expect(msUntilExpiry('nope')).toBe(0);
  });
});

describe('stepStates follows RequestStatus', () => {
  const states = (s, approval) => stepStates(s, approval).map((x) => `${x.key}:${x.state}`);

  it('skips approval steps when the type needs none', () => {
    expect(stepStates('PROCESSING', false).map((s) => s.key)).toEqual(['SUBMITTED', 'VERIFIED', 'PROCESSING', 'COMPLETED']);
    expect(states('PROCESSING', false)).toEqual(['SUBMITTED:done', 'VERIFIED:done', 'PROCESSING:current', 'COMPLETED:todo']);
  });
  it('marks a forwarded request as waiting on approval', () => {
    expect(states('FORWARDED', true)[2]).toBe('FORWARDED:current');
  });
  it('flags an incomplete request at the first step', () => {
    const [first] = stepStates('INCOMPLETE', true);
    expect(first).toMatchObject({ state: 'attention', label: 'Needs more info' });
  });
  it('shows rejection in place of approval', () => {
    expect(states('REJECTED', true)).toEqual([
      'SUBMITTED:done',
      'VERIFIED:done',
      'FORWARDED:done',
      'APPROVED:rejected',
      'PROCESSING:todo',
      'COMPLETED:todo',
    ]);
  });
  it('completes every step', () => {
    expect(stepStates('COMPLETED', true).every((s) => s.state === 'done')).toBe(true);
  });
});

describe('relativeTime', () => {
  const now = Date.parse('2026-11-11T12:00:00Z');
  it.each([
    ['2026-11-11T11:59:40Z', 'just now'],
    ['2026-11-11T11:45:00Z', '15 min ago'],
    ['2026-11-11T09:00:00Z', '3 h ago'],
    ['2026-11-10T12:00:00Z', '1 day ago'],
  ])('%s -> %s', (iso, expected) => expect(relativeTime(iso, now)).toBe(expected));
});
