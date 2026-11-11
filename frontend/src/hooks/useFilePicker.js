import { useState } from 'react';
import { validateFile } from '../lib/format';

/** File input state with client-side validation (extension, size, magic bytes). */
export function useFilePicker() {
  const [file, setFile] = useState(null);
  const [error, setError] = useState(null);

  async function onFile(e) {
    const input = e.target;
    const picked = input.files?.[0] ?? null;
    const message = await validateFile(picked);
    setError(message);
    setFile(message ? null : picked);
    if (message) input.value = '';
  }

  return { file, error, onFile, reset: () => { setFile(null); setError(null); } };
}
