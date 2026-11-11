import { PURPOSE_MAX_LENGTH, UPLOAD } from '../config';
import { formatBytes } from '../lib/format';

/**
 * The editable part of a request: the type's required fields, purpose and an attachment.
 * Shared by the new-request form and the resubmit form. Field names match the backend's
 * fieldErrors keys ("purpose", "attachment", or the required field's own name).
 */
export default function RequestFields({ type, values, onChange, file, onFile, errors = {}, disabled }) {
  const setField = (name) => (e) => onChange({ ...values, fields: { ...values.fields, [name]: e.target.value } });

  return (
    <fieldset className="fieldset" disabled={disabled}>
      {type.requiredFields.map((name) => (
        <label key={name} className="field">
          <span>
            {name} <abbr title="required">*</abbr>
          </span>
          <input
            value={values.fields[name] ?? ''}
            onChange={setField(name)}
            aria-invalid={Boolean(errors[name])}
            maxLength={200}
          />
          {errors[name] && <small className="field__error">{errors[name]}</small>}
        </label>
      ))}

      <label className="field">
        <span>
          Purpose <abbr title="required">*</abbr>
        </span>
        <textarea
          rows={3}
          value={values.purpose}
          onChange={(e) => onChange({ ...values, purpose: e.target.value })}
          maxLength={PURPOSE_MAX_LENGTH}
          aria-invalid={Boolean(errors.purpose)}
          placeholder="e.g. Required for a passport application"
        />
        <small className={`field__hint ${values.purpose.length >= PURPOSE_MAX_LENGTH ? 'field__hint--limit' : ''}`}>
          {values.purpose.length}/{PURPOSE_MAX_LENGTH}
        </small>
        {errors.purpose && <small className="field__error">{errors.purpose}</small>}
      </label>

      <label className="field">
        <span>
          Supporting document {type.attachmentRequired ? <abbr title="required">*</abbr> : <em className="muted">(optional)</em>}
        </span>
        <input type="file" accept={UPLOAD.accept} onChange={onFile} aria-invalid={Boolean(errors.attachment)} />
        <small className="field__hint">
          PDF, JPG or PNG, up to {formatBytes(UPLOAD.maxBytes)}.
          {file && ` Selected: ${file.name} (${formatBytes(file.size)}).`}
        </small>
        {errors.attachment && <small className="field__error">{errors.attachment}</small>}
      </label>
    </fieldset>
  );
}

/** Client-side checks that mirror RequestValidator.validateSubmission. Returns {field: message}. */
export function validateRequestValues(type, values, file) {
  const errors = {};
  for (const name of type.requiredFields) {
    if (!values.fields[name]?.trim()) errors[name] = `${name} is required.`;
  }
  if (!values.purpose.trim()) errors.purpose = 'Purpose is required.';
  else if (values.purpose.length > PURPOSE_MAX_LENGTH) {
    errors.purpose = `Purpose must be at most ${PURPOSE_MAX_LENGTH} characters.`;
  }
  if (type.attachmentRequired && !file) errors.attachment = 'A supporting file is required.';
  return errors;
}

export function UploadProgress({ value }) {
  if (value == null) return null;
  return (
    <div className="progress" role="progressbar" aria-valuenow={value} aria-valuemin={0} aria-valuemax={100}>
      <div className="progress__bar" style={{ width: `${value}%` }} />
      <span>{value < 100 ? `Uploading ${value}%` : 'Saving…'}</span>
    </div>
  );
}
