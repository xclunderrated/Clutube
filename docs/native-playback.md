# Stream playback

CluTube exposes three app-level providers:

1. VidSrc is the default player. The app loads the documented VidSrc embed
   endpoint in its WebView and preserves the provider's native quality and
   subtitle controls.
2. VidLink Pro is the secondary provider and keeps its existing embed URL and
   WebView loading path.
3. VidFast is the tertiary provider: a fast multi-server player keyed by TMDB
   ID (`vidfast.pro/movie/{id}`, `vidfast.pro/tv/{id}/{season}/{episode}`).

## VidSrc mirror preferences

The VidSrc player exposes the following mirrors in its glass server selector:

```text
vidsrc2.ru
vidsrc.ir
vidsrcme.ru
vidsrcme.su
vidsrc-me.ru
vidsrc-me.su
vidsrc-embed.ru
vidsrc-embed.su
vsrc.su
vidsrc.pm
vidsrc.to
vidsrc.cc
```

Most mirrors share the standard `/embed/...` path; `vidsrc.cc` uses the
versioned `/v2/embed/...` path (handled per-host by the app).

Users can select a mirror, drag it into a new position, use the move arrows,
and save the preferred order. The order and selected mirror are stored in
Android `SharedPreferences` and mirrored into the embedded player storage.

## Failover behavior

- The player tries the selected VidSrc mirror first, then the remaining mirrors
  in the saved order.
- A failed VidSrc embed or mirror HTTP error advances to the next mirror in
  the saved order.
- If every VidSrc attempt fails, the Android playback coordinator switches to
  VidLink Pro, then VidFast, automatically.
- If VidLink Pro fails, the coordinator tries VidFast, then returns to VidSrc,
  showing the final retry/server surface only after all providers are exhausted.

## Quality switching

The VidSrc player exposes Auto and available resolution choices through its
native settings menu. The selected quality is applied without changing the
provider.

## Deployment note

The player depends on the configured VidSrc service and its documented embed
dependencies at runtime. Verify upstream availability, rights, terms of
service, and local law before distributing or operating the application.
