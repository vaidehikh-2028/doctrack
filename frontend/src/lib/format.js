import { STATUS, UPLOAD } from '../config';

export const statusLabel = (s) => STATUS[s] ?? s;

export const titleCase = (s) =>
  String(s ?? '')
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/^\w/, (c) => c.toUpperCase());

export function formatDateTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function relativeTime(iso, now = Date.now()) {
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return '';
  const mins = Math.round((now - t) / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins} min ago`;
  const hrs = Math.round(mins / 60);
  if (hrs < 24) return `${hrs} h ago`;
  const days = Math.round(hrs / 24);
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

export function formatBytes(n) {
  if (n == null) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${Math.round(n / 1024)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

const MAGIC = {
  pdf: [[0x25, 0x50, 0x44, 0x46]], // %PDF
  png: [[0x89, 0x50, 0x4e, 0x47]], // .PNG
  jpg: [[0xff, 0xd8, 0xff]],
  jpeg: [[0xff, 0xd8, 0xff]],
};

async function headBytes(file, n) {
  const blob = file.slice(0, n);
  const buf = typeof blob.arrayBuffer === 'function'
    ? await blob.arrayBuffer()
    : await new Promise((resolve, reject) => {
        const r = new FileReader();
        r.onload = () => resolve(r.result);
        r.onerror = () => reject(r.error);
        r.readAsArrayBuffer(blob);
      });
  return new Uint8Array(buf);
}

/**
 * Client-side mirror of RequestValidator.validateAttachment: extension, size (5 MB inclusive)
 * and magic bytes, so a renamed executable is caught before upload. The server still re-checks.
 * Resolves to an error message, or null when the file is acceptable.
 */
export async function validateFile(file, rules = UPLOAD) {
  if (!file) return null;
  const ext = file.name.toLowerCase().split('.').pop();
  if (!rules.extensions.includes(ext) || file.size === 0 || file.size > rules.maxBytes) {
    return 'Only PDF, JPG or PNG files up to 5 MB are allowed.';
  }
  const head = await headBytes(file, 4);
  const ok = MAGIC[ext].some((sig) => sig.every((b, i) => head[i] === b));
  return ok ? null : `This file is not a real ${ext.toUpperCase()}.`;
}
