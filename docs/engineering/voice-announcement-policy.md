# Live Voice Announcement Policy

Status: implemented as a conservative duplicate-suppression policy for detector events.

## Goals

- Avoid repeating the same visible object every camera frame.
- Require consecutive observations before general foreground narration.
- Treat the same detector label in a different image direction as a separate visual event.
- Re-arm a visual event only after a meaningful absence interval, so brief detector dropouts do not cause repeated speech.
- Permit one higher-severity escalation when an already announced obstacle becomes close/approaching or very-close-looking.
- Mark an announcement only after Android Text-to-Speech accepts it; failed playback resets the policy so a retry remains possible.

## Event identity and limitations

The current detector does not provide persistent tracking IDs. The policy therefore uses the normalized detector label and coarse image direction as a visual-event key, such as `chair|ahead`. It cannot reliably distinguish two identical chairs in the same direction or prove that a newly detected chair is the same physical chair. A future object-tracking component should replace this coarse key with persistent track IDs.

## Current timing policy

- Foreground narration: two consecutive observations; re-announce the same label only after it has been absent for at least five seconds, regardless of small direction changes.
- Obstacle narration: no repeat at the same severity while continuously detected; re-arm after five seconds of absence.
- Severity escalation: one higher-severity update is eligible after a 1.5-second escalation cooldown.
- With valid calibration, a distance update is eligible only after the estimated range centre moves at least 30% closer or the lower step bound drops by at least two steps; the same cooldown applies.
- Small distance fluctuations do not trigger speech.
- Old visual-event memory is pruned after 60 seconds.
- Existing global speech cooldown and urgent-warning behavior remain in place.

These are announcement-management timings, not sensor sampling guarantees or safety thresholds.

## Distance and step wording

- Metric distance and step bands are spoken only when a fresh supported calibration and measured stride length are available.
- Values are broad approximate ranges derived from monocular image geometry and class-size priors.
- The app must not describe these estimates as exact distance, remaining collision distance, or a clear/safe route.
- Stride is calculated from a measured indoor walking trial: measured distance divided by counted steps. No generic stride default is substituted.

## Validation

Unit tests cover consecutive-observation gating, duplicate suppression, re-arming after absence, direction-specific foreground events, obstacle severity escalation, and stride calculation input validation. Physical-device validation is still required for real detector flicker, TTS interruptions, and user-specific calibration.
