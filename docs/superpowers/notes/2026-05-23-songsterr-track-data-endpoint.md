# Songsterr track-data endpoint (discovered 2026-05-23)

Discovered by loading the Songsterr web player for IATA in a real browser
and watching network traffic. See `app/src/test/resources/songsterr/revision-sample.json`
for the captured response.

## The full fetch chain

```
1. GET https://www.songsterr.com/api/songs?pattern={query}
   → array; pick `songId` from a chosen result

2. GET https://www.songsterr.com/api/meta/{songId}
   → object with `revisionId`, `image` (slug), and `tracks[]`
   → from `tracks[]` and `popularTrackDrum` (index), get the drum track's
     `partId` (which equals its index in the array)

3. GET https://{cdnHost}/{songId}/{revisionId}/{image}/{partId}.json
   → gzipped JSON track data (must request with Accept-Encoding: gzip OR
     use OkHttp's transparent gzip — OkHttp does it by default)

   `{cdnHost}` is one of TWO known CloudFront distributions (see "CDN
   hosts" below). The fetcher tries each in order until one returns 2xx.
```

The `/api/meta/{songId}/revisions` endpoint exists too but it only returns
revision history metadata; it does NOT include the `image` slug.
`/api/meta/{songId}` is the single endpoint that gives revisionId + image
together.

## CDN hosts

Songsterr serves track-data from at least two CloudFront distributions.
The web player has the host list baked into its bundled JS; the meta
API does not expose which host any given song lives on.

| Host                              | Carries (observed)                                    |
|-----------------------------------|-------------------------------------------------------|
| `d3d3l6a6rcgkaf.cloudfront.net`   | Modern tabs (image slug starts with `v0-…-stage`) — the majority of popular songs (Nirvana, Led Zeppelin, AC/DC, Radiohead, …) |
| `dqsljvtekg760.cloudfront.net`    | Legacy tabs (image slug starts with `v5-`) — older / less-trafficked songs (e.g. Phil Collins "In The Air Tonight" songId 50420) |

The slug-prefix correlation looks deterministic but isn't documented,
so we don't depend on it — `OkHttpSongsterrTabFetcher` tries each host
in `DEFAULT_CDN_BASE_URLS` order until one returns 2xx. If Songsterr
adds a third distribution, capture it via Playwright (same method as
Task 8 in the Phase 3 plan) and prepend it to the constant.

## URL pattern

`https://{cdnHost}/{songId}/{revisionId}/{image}/{partId}.json`

Where:
- `songId`: Long (e.g. `50420`)
- `revisionId`: Long (e.g. `5548296`)
- `image`: opaque string (e.g. `v5-2-2-I27Eizzw7M8iquom`) — comes from
  `meta` API. The `v5-` prefix is a Songsterr internal version of the
  cloudfront layout — distinct from the file-level `version: 8` inside
  the JSON itself.
- `partId`: Int — track index in `meta.tracks[]`. For the drum track,
  use `meta.popularTrackDrum` (returns the partId directly).

## Method + headers

- Method: `GET`
- No auth / no cookies required.
- The Songsterr browser sends `Referer: https://www.songsterr.com/` but
  the endpoint works without it. We send it anyway (cheap robustness).
- Response is gzipped (Content-Type says `application/json` but the body
  starts with `1f 8b` — gzip magic). OkHttp transparently decompresses.

## Response shape (top-level fields used by `DrumTabParser`)

```jsonc
{
  "name": "Phil Collins - Roland CR-78 | Drum Machine",
  "balance": 0,
  "volume": 1,
  "instrumentId": 1024,
  "instrument": "Drums",
  "partId": 10,
  "songId": 50420,
  "revisionId": 5548296,
  "version": 8,
  "automations": {
    "tempo": [
      { "measure": 0, "position": 0, "bpm": 95, "type": 4 }
    ]
  },
  "measures": [
    {
      "signature": [4, 4],
      "marker": { "text": "Intro", "width": 38 },   // optional, may be null
      "voices": [
        {
          "beats": [
            {
              "notes": [
                { "fret": 46, "string": -0.5, "accentuated": 1 }
              ],
              "velocity": "p",
              "type": 8,
              "duration": [1, 8],
              "beamStart": true
            },
            {
              "notes": [{ "rest": true }],
              "rest": true,
              "type": 8,
              "duration": [1, 8]
            }
          ]
        }
      ]
    }
  ]
}
```

## Where each parser-required value lives

| Need | JSON path | Notes |
|------|-----------|-------|
| BPM | `automations.tempo[0].bpm` | First entry; tempo changes are ignored per spec |
| Time sig numerator | `measures[0].signature[0]` | First bar's meter |
| Time sig denominator | `measures[0].signature[1]` | First bar's meter |
| Bars list | `measures` | Array, one entry per bar |
| Per-bar meter (for change detection) | `measures[i].signature` | Present on every measure — emit warning if changes |
| Per-bar beats | `measures[i].voices[0].beats` | First voice. Songs may have multiple voices in the same staff but for drums we treat them as a single sequence; if multi-voice support needed later, iterate all voices and merge slot-wise |
| Beat duration | `measures[i].voices[v].beats[b].duration` | `[num, denom]` — e.g. `[1, 8]` is an eighth, `[1, 16]` is a sixteenth |
| Beat is rest | `measures[i].voices[v].beats[b].rest == true` OR `notes[0].rest == true` | Either flag means "no notes hit" |
| Notes hit at this beat | `measures[i].voices[v].beats[b].notes[]` | Array — multiple notes per beat = simultaneous drums |
| MIDI percussion number | `notes[k].fret` | Yes, for percussion `fret` IS the MIDI percussion number. Map via `MidiPercussionMap` |

## Slot quantization

`slotsPerBar` defaults to 16. Detect triplet feel by scanning beat
durations for non-power-of-2 denominators (e.g. `[1, 12]` or `[1, 24]`)
or via a `tuplet` field on the beat (capture more fixtures to confirm).
When triplets are present, use 24 slots/bar instead.

Position of a beat within its bar:
- Walk `voices[0].beats` cumulatively. After each beat, advance position
  by `duration.numerator / duration.denominator` (as a fraction of a
  whole note).
- Convert to slot index: `slot = round(position * slotsPerBar * denom_of_meter / num_of_meter * num_of_meter / denom_of_meter)` —
  simplification: in a 4/4 bar where the bar holds `1.0` worth of whole
  notes, slot = `position * slotsPerBar`. If a beat falls at position
  0.25 in a 4/4 bar, slot = `0.25 * 16 = 4`.

## Stability notes

- `version: 8` at the file level is versioned. If Songsterr bumps it,
  the fixture-based regression tests catch it.
- The image-slug prefix (`v5-` legacy, `v0-…-stage` modern) appears to
  correlate with CDN host but isn't load-bearing — we try each host
  rather than parse the slug. See "CDN hosts" above.
- No rate-limit observed at typical browsing speed (~10s between songs).
  Aggressive scripted fetches may hit one.
