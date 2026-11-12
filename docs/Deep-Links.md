# Deep links

| Link | Use |
|---|---|
| `voyager://auth/callback?code=\|error=` | Return from social sign-in in the system browser |
| `https://<web host>/verify?token=` | Email verification (R3) |
| `https://<web host>/reset-password?token=` | Password reset (R3) |
| `voyager://share`, `alerts`, `plan`, `chat`, `nearby` | Existing in-app targets |

- The https filters are not `autoVerify` (needs `assetlinks.json` with the release cert fingerprint, owner task), so Android may show a chooser once. The app always offers "paste the link or code" as a fallback.
- `DeepLinkIntents.sanitize` rejects other hosts, long tokens and extra paths; it is unit-tested.
- Cold-start links wait for the session gate (`DeepLinkHold`).
