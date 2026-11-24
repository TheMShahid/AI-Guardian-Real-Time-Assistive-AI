# Live Voice Announcement Policy

Status: implemented as a conservative duplicate-suppression policy for detector events.

## Goals

- Wait for a stable camera warm-up before speaking live detections.
- Let a newly stable object or obstacle replace stale lower-priority speech.
- Never automatically describe an unobserved sector as clear or safe.
- Avoid repeating the same visible object every camera frame.
- Require consecutive observations before general foreground narration.
- Treat the same detector label in a different image direction as a separate visual event.
- Re-arm a visual event only after a meaningful absence interval, so brief detector dropouts do not cause repeated speech.
- Permit one higher-severity escalation when an already announced obstacle becomes close/approaching or very-close-looking.
- Mark an announcement only after Android Text-to-Speech accepts it; failed playback resets the policy so a retry remains possible.

## Event identity and limitations

The current detector does not provide persistent tracking IDs. General foreground narration combines the normalized label with a visual-focus heuristic based on normalized bounding-box centre and scale. Small shifts are treated as the same focus; a material position/scale change can make a second object of the same class eligible after two observations. Walking-obstacle alerts use the normalized label plus coarse image direction, such as `chair|front-center`. These are visual-event heuristics, not guaranteed physical identities; two identical objects in nearly the same image position may still be indistinguishable. A future object-tracking component should replace these coarse keys with persistent track IDs.

## Current timing policy

- Foreground narration: two consecutive observations; re-announce the same label only after it has been absent for at least five seconds, regardless of small direction changes.
- Camera startup: wait for at least five distinct detector frames and 1.5 seconds after session start before any live object/obstacle narration.
- New focus: a newly stable foreground object or obstacle can interrupt the previous lower-priority utterance; confirmed obstacle events take priority over general object narration.
- Empty view: after four distinct consecutive empty detector frames, speak a neutral "no supported object detected in the current camera view" status. Repeat at most every 15 seconds while the view remains empty; reset immediately when detections return. This is not a claim that the route is clear or safe.
- Empty-view feedback is suppressed while distance calibration capture is active and never flushes an obstacle warning.
- Foreground distance updates: after the first object announcement, permit another update only when a calibrated range centre is at least 30% closer or the lower step bound improves by at least two steps, with a two-second minimum interval. The update replaces stale foreground narration but never takes priority over a newly confirmed obstacle.
- Obstacle narration: no repeat at the same severity while continuously detected; re-arm after five seconds of absence.
- Severity escalation: one higher-severity update is eligible after a 1.5-second escalation cooldown.
- With valid calibration, a distance update is eligible only after the estimated range centre moves at least 30% closer or the lower step bound drops by at least two steps; the same cooldown applies.
- Small distance fluctuations do not trigger speech.
- Old visual-event memory is pruned after 60 seconds.
- Per-object event memory and escalation cooldowns replace a global routine speech cooldown. New confirmed objects are not delayed by unrelated previous announcements.

These are announcement-management timings, not sensor sampling guarantees or safety thresholds.

## Distance and step wording

- Metric distance and step bands are spoken only when a fresh supported calibration and measured stride length are available.
- Values are broad approximate ranges derived from monocular image geometry and class-size priors.
- The app must not describe these estimates as exact distance, remaining collision distance, or a clear/safe route.
- Stride is calculated from a measured indoor walking trial: measured distance divided by counted steps. No generic stride default is substituted.

## Validation

Unit tests cover camera startup gating, distinct-frame counting, foreground object switching, duplicate suppression, re-arming after absence, calibrated foreground distance wording, obstacle severity escalation, and stride calculation input validation. Physical-device validation is still required for real detector flicker, TTS queue interruption timing, camera-turn focus changes, and user-specific calibration.
