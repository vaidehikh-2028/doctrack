/**
 * Progress through the lifecycle in RequestStatus. The approval steps appear only for
 * document types that need approval, matching VerificationService (BR-004).
 *
 *   SUBMITTED -> VERIFIED -> [FORWARDED -> APPROVED] -> PROCESSING -> COMPLETED
 *   side exits: INCOMPLETE (back to SUBMITTED), REJECTED (final)
 */
export function stepsFor(approvalRequired) {
  return [
    { key: 'SUBMITTED', label: 'Submitted' },
    { key: 'VERIFIED', label: 'Verified' },
    ...(approvalRequired
      ? [
          { key: 'FORWARDED', label: 'Awaiting approval' },
          { key: 'APPROVED', label: 'Approved' },
        ]
      : []),
    { key: 'PROCESSING', label: 'Processing' },
    { key: 'COMPLETED', label: 'Ready' },
  ];
}

/** Returns [{key, label, state}] where state is done | current | todo | attention | rejected. */
export function stepStates(status, approvalRequired) {
  const steps = stepsFor(approvalRequired);
  if (status === 'INCOMPLETE') {
    return steps.map((s, i) =>
      i === 0 ? { ...s, label: 'Needs more info', state: 'attention' } : { ...s, state: 'todo' },
    );
  }
  if (status === 'REJECTED') {
    const at = steps.findIndex((s) => s.key === 'APPROVED');
    return steps.map((s, i) => {
      if (i < at) return { ...s, state: 'done' };
      if (i === at) return { ...s, label: 'Rejected', state: 'rejected' };
      return { ...s, state: 'todo' };
    });
  }
  const current = steps.findIndex((s) => s.key === status);
  return steps.map((s, i) => {
    if (status === 'COMPLETED' || i < current) return { ...s, state: 'done' };
    if (i === current) return { ...s, state: 'current' };
    return { ...s, state: 'todo' };
  });
}

export default function StatusStepper({ status, approvalRequired }) {
  const steps = stepStates(status, approvalRequired);
  return (
    <ol className="stepper" aria-label="Request progress">
      {steps.map((s) => (
        <li
          key={s.key}
          className={`stepper__step stepper__step--${s.state}`}
          aria-current={s.state === 'current' || s.state === 'attention' ? 'step' : undefined}
        >
          <span className="stepper__dot" aria-hidden="true" />
          <span className="stepper__label">{s.label}</span>
        </li>
      ))}
    </ol>
  );
}
