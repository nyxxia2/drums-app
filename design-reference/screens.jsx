// screens.jsx — All 5 screens for the Drums app, parameterized by theme.
// Each screen renders inside a phone viewport (the Android frame inner area).

// ─────────────────────────────────────────────────────────────
// THEMES — 3 visual directions
// ─────────────────────────────────────────────────────────────
const THEMES = {
  studio: {
    name: 'Neon Studio',
    bg: '#0a0a0c',
    surface: '#131318',
    surface2: '#1c1c24',
    line: '#26262e',
    text: '#f5f5f7',
    dim: '#8a8a94',
    accent: '#ff2a3d',
    accent2: '#ffd84d',
    display: '"Space Grotesk", system-ui, sans-serif',
    body: '"Space Grotesk", system-ui, sans-serif',
    mono: '"JetBrains Mono", ui-monospace, monospace',
    radius: 14,
    statusDark: true,
    staffColors: {
      line: '#3a3a44', note: '#f5f5f7', accent: '#ff2a3d',
      playhead: '#ff2a3d', barLine: '#3a3a44',
    },
    barColor: '#26262e',
    barFill: '#ff2a3d',
    chip: 'rgba(255,255,255,0.08)',
    chipText: '#f5f5f7',
    btnPrimary: '#ff2a3d',
    btnPrimaryText: '#0a0a0c',
    coverGrad: ['#ff2a3d', '#7a1020'],
  },
  stage: {
    name: 'Heavy Stage',
    bg: '#fff5cc',
    surface: '#ffffff',
    surface2: '#0a0a0a',
    line: '#0a0a0a',
    text: '#0a0a0a',
    dim: '#5a5a5a',
    accent: '#ff0066',
    accent2: '#0033ff',
    display: '"Archivo Black", "Archivo", system-ui, sans-serif',
    body: '"Archivo", system-ui, sans-serif',
    mono: '"DM Mono", ui-monospace, monospace',
    radius: 4,
    statusDark: false,
    staffColors: {
      line: '#0a0a0a', note: '#0a0a0a', accent: '#ff0066',
      playhead: '#ff0066', barLine: '#0a0a0a',
    },
    barColor: 'rgba(10,10,10,0.12)',
    barFill: '#0a0a0a',
    chip: '#0a0a0a',
    chipText: '#fff5cc',
    btnPrimary: '#0a0a0a',
    btnPrimaryText: '#fff5cc',
    coverGrad: ['#ff0066', '#ffe600'],
  },
  warm: {
    name: 'Warm Rhythm',
    bg: '#f4ead4',
    surface: '#fbf6e8',
    surface2: '#ede0c4',
    line: '#d8c8a4',
    text: '#2a1810',
    dim: '#7a6856',
    accent: '#e85a1a',
    accent2: '#3a6b4a',
    display: '"Bricolage Grotesque", system-ui, sans-serif',
    body: '"Bricolage Grotesque", system-ui, sans-serif',
    mono: '"DM Mono", ui-monospace, monospace',
    serif: '"Instrument Serif", Georgia, serif',
    radius: 18,
    statusDark: false,
    staffColors: {
      line: '#2a1810', note: '#2a1810', accent: '#e85a1a',
      playhead: '#e85a1a', barLine: '#2a1810',
    },
    barColor: '#e2d3ad',
    barFill: '#e85a1a',
    chip: '#ede0c4',
    chipText: '#2a1810',
    btnPrimary: '#2a1810',
    btnPrimaryText: '#fbf6e8',
    coverGrad: ['#e85a1a', '#f3c34d'],
  },
};

// ─────────────────────────────────────────────────────────────
// Shared building blocks
// ─────────────────────────────────────────────────────────────
function PhoneCanvas({ theme, children, padding = 16, scroll = true }) {
  return (
    <div style={{
      width: '100%', height: '100%', background: theme.bg, color: theme.text,
      fontFamily: theme.body, padding,
      overflow: scroll ? 'auto' : 'hidden',
      display: 'flex', flexDirection: 'column', gap: 14,
    }}>{children}</div>
  );
}

function IconBtn({ children, theme, size = 40, style }) {
  return (
    <div style={{
      width: size, height: size, borderRadius: 999,
      background: 'transparent', border: `1.4px solid ${theme.line}`,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      color: theme.text, ...style,
    }}>{children}</div>
  );
}

// Tiny SVG icons (stroke-based, theme-aware)
const Icon = {
  play: (s = 14, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 16 16" fill={c}><path d="M4 2l10 6-10 6V2z"/></svg>
  ),
  pause: (s = 14, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 16 16" fill={c}><rect x="3" y="2" width="3.5" height="12"/><rect x="9.5" y="2" width="3.5" height="12"/></svg>
  ),
  stop: (s = 14, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 16 16" fill={c}><rect x="3" y="3" width="10" height="10"/></svg>
  ),
  upload: (s = 16, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 16 16" fill="none" stroke={c} strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round"><path d="M8 11V2M5 5l3-3 3 3M3 14h10"/></svg>
  ),
  camera: (s = 16, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 16 16" fill="none" stroke={c} strokeWidth={1.4} strokeLinecap="round" strokeLinejoin="round"><path d="M2 5h2.5l1-1.5h5L11.5 5H14v8H2z"/><circle cx="8" cy="9" r="2.4"/></svg>
  ),
  file: (s = 16, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 16 16" fill="none" stroke={c} strokeWidth={1.4} strokeLinejoin="round"><path d="M3 1.5h6L13 5v9.5H3z"/><path d="M9 1.5V5h4"/></svg>
  ),
  spotify: (s = 16, c = '#1DB954') => (
    <svg width={s} height={s} viewBox="0 0 16 16" fill={c}><circle cx="8" cy="8" r="7.5"/><path d="M4 6.5c2.4-.6 5.4-.5 7.5.8M4.3 9c2-.4 4.4-.3 6.2.7M4.7 11.2c1.7-.3 3.4-.2 4.8.6" stroke="#000" strokeWidth={1.1} strokeLinecap="round" fill="none"/></svg>
  ),
  more: (s = 16, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 16 16" fill={c}><circle cx="3" cy="8" r="1.4"/><circle cx="8" cy="8" r="1.4"/><circle cx="13" cy="8" r="1.4"/></svg>
  ),
  back: (s = 16, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 16 16" fill="none" stroke={c} strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round"><path d="M10 2L4 8l6 6"/></svg>
  ),
  plus: (s = 16, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 16 16" fill="none" stroke={c} strokeWidth={1.7} strokeLinecap="round"><path d="M8 2v12M2 8h12"/></svg>
  ),
  metronome: (s = 16, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 16 16" fill="none" stroke={c} strokeWidth={1.4} strokeLinejoin="round" strokeLinecap="round"><path d="M5 14L7 1h2l2 13zM3 14h10"/><line x1="8" y1="11" x2="11.5" y2="3"/></svg>
  ),
  loop: (s = 16, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 16 16" fill="none" stroke={c} strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round"><path d="M3 7V5a2 2 0 012-2h7l-2-2M13 9v2a2 2 0 01-2 2H4l2 2"/></svg>
  ),
  speed: (s = 16, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 16 16" fill="none" stroke={c} strokeWidth={1.4} strokeLinecap="round" strokeLinejoin="round"><path d="M2 11a6 6 0 1112 0"/><path d="M8 11l3-3"/></svg>
  ),
};

// Placeholder cover art — diagonal stripes + initial
function CoverArt({ theme, label = 'NV', size = 56, radius }) {
  const [c1, c2] = theme.coverGrad;
  const r = radius ?? theme.radius;
  return (
    <div style={{
      width: size, height: size, borderRadius: r, flexShrink: 0,
      background: `linear-gradient(135deg, ${c1} 0%, ${c2} 100%)`,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      color: '#fff', fontFamily: theme.display,
      fontSize: size * 0.34, fontWeight: 800, letterSpacing: -1,
      position: 'relative', overflow: 'hidden',
    }}>
      <svg style={{ position: 'absolute', inset: 0, opacity: 0.25 }} viewBox="0 0 56 56">
        {[-40,-20,0,20,40].map(o => (
          <line key={o} x1={o} y1={0} x2={o + 56} y2={56} stroke="#fff" strokeWidth={1.5} />
        ))}
      </svg>
      <span style={{ position: 'relative', zIndex: 1 }}>{label}</span>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// HOME — library of songs
// ─────────────────────────────────────────────────────────────
function HomeScreen({ theme }) {
  const songs = [
    { t: 'Smells Like Teen Spirit', a: 'Nirvana', bpm: 116, last: 'Yesterday', label: 'NV' },
    { t: 'Tom Sawyer', a: 'Rush', bpm: 88, last: '3d ago', label: 'RU' },
    { t: 'Rosanna', a: 'Toto', bpm: 86, last: '1w ago', label: 'TO' },
    { t: 'In the Air Tonight', a: 'Phil Collins', bpm: 95, last: '2w ago', label: 'PC' },
    { t: 'YYZ', a: 'Rush', bpm: 144, last: 'Imported', label: 'RU' },
  ];
  const tab = (label, active) => (
    <div key={label} style={{
      padding: '8px 14px', borderRadius: 999,
      background: active ? theme.text : 'transparent',
      color: active ? theme.bg : theme.dim,
      border: active ? 'none' : `1px solid ${theme.line}`,
      fontWeight: 600, fontSize: 13, letterSpacing: -0.1,
      whiteSpace: 'nowrap',
    }}>{label}</div>
  );
  return (
    <PhoneCanvas theme={theme}>
      {/* header */}
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginTop: 4 }}>
        <div>
          <div style={{ fontSize: 12, fontFamily: theme.mono, letterSpacing: 1, color: theme.dim, textTransform: 'uppercase' }}>Library</div>
          <h1 style={{
            margin: '4px 0 0', fontFamily: theme.display, fontSize: 32, lineHeight: 1,
            letterSpacing: theme === THEMES.stage ? -1.2 : -1, fontWeight: 800,
            textTransform: theme === THEMES.stage ? 'uppercase' : 'none',
          }}>
            {theme === THEMES.warm ? (
              <><span style={{ fontFamily: theme.serif, fontStyle: 'italic', fontWeight: 400 }}>your</span> kit</>
            ) : 'Your kit'}
          </h1>
        </div>
        <IconBtn theme={theme} size={36}>{Icon.more(14)}</IconBtn>
      </div>

      {/* tabs */}
      <div style={{ display: 'flex', gap: 6, overflow: 'hidden' }}>
        {tab('All 24', true)}
        {tab('Recent', false)}
        {tab('Spotify', false)}
      </div>

      {/* now-practicing card */}
      <NowPracticingCard theme={theme} song={songs[0]} />

      {/* list */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <div style={{ fontSize: 11, fontFamily: theme.mono, letterSpacing: 1.5, color: theme.dim, textTransform: 'uppercase', padding: '12px 4px 6px' }}>
          All songs
        </div>
        {songs.slice(1).map((s, i) => (
          <SongRow key={i} theme={theme} song={s} />
        ))}
      </div>

      <div style={{ height: 60 }} />

      {/* FAB */}
      <div style={{
        position: 'absolute', right: 20, bottom: 50,
        width: 56, height: 56, borderRadius: theme === THEMES.stage ? 6 : 28,
        background: theme.accent, color: theme === THEMES.studio ? '#0a0a0c' : '#fff',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        boxShadow: '0 10px 24px rgba(0,0,0,0.18)',
      }}>{Icon.plus(20, 'currentColor')}</div>
    </PhoneCanvas>
  );
}

function NowPracticingCard({ theme, song }) {
  return (
    <div style={{
      background: theme.surface, borderRadius: theme.radius, padding: 16,
      border: theme === THEMES.stage ? `2px solid ${theme.text}` : 'none',
      display: 'flex', flexDirection: 'column', gap: 12,
      position: 'relative', overflow: 'hidden',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <CoverArt theme={theme} label={song.label} size={48} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 10, fontFamily: theme.mono, color: theme.accent, letterSpacing: 1.5, textTransform: 'uppercase', fontWeight: 700 }}>
            ⟶ Continue
          </div>
          <div style={{ fontSize: 17, fontWeight: 700, fontFamily: theme.display, letterSpacing: -0.3, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{song.t}</div>
          <div style={{ fontSize: 12, color: theme.dim }}>{song.a} · {song.bpm} BPM</div>
        </div>
      </div>
      {/* mini staff preview */}
      <div style={{ marginLeft: -4, marginRight: -4 }}>
        <DrumStaff bars={[BAR_GROOVE, BAR_GROOVE_2]} barIndex={0} barCount={2}
          currentBeat={11} showClef={false} colors={theme.staffColors}
          width={300} height={68} />
      </div>
      {/* progress */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{ flex: 1, height: 4, background: theme.barColor, borderRadius: 2, overflow: 'hidden' }}>
          <div style={{ width: '34%', height: '100%', background: theme.barFill }} />
        </div>
        <div style={{ fontSize: 11, fontFamily: theme.mono, color: theme.dim, fontVariantNumeric: 'tabular-nums' }}>1:14 / 3:32</div>
        <div style={{
          width: 36, height: 36, borderRadius: 18, background: theme.accent,
          color: theme === THEMES.studio ? '#0a0a0c' : '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>{Icon.play(12, 'currentColor')}</div>
      </div>
    </div>
  );
}

function SongRow({ theme, song }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 12,
      padding: '10px 4px', borderBottom: `1px solid ${theme.line}`,
    }}>
      <CoverArt theme={theme} label={song.label} size={40} radius={theme === THEMES.stage ? 4 : 8} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 14, fontWeight: 600, letterSpacing: -0.2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{song.t}</div>
        <div style={{ fontSize: 11, color: theme.dim, fontFamily: theme.mono }}>{song.a} · {song.bpm}BPM · {song.last}</div>
      </div>
      <div style={{ color: theme.dim }}>{Icon.more(14)}</div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// UPLOAD — import flow
// ─────────────────────────────────────────────────────────────
function UploadScreen({ theme }) {
  const opt = (icon, title, sub, accent) => (
    <div style={{
      background: theme.surface, borderRadius: theme.radius, padding: 16,
      border: theme === THEMES.stage ? `2px solid ${theme.text}` : `1px solid ${theme.line}`,
      display: 'flex', alignItems: 'center', gap: 14,
    }}>
      <div style={{
        width: 44, height: 44, borderRadius: theme === THEMES.stage ? 4 : 12,
        background: accent || theme.surface2, color: accent ? '#fff' : theme.text,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        flexShrink: 0,
      }}>{icon}</div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 15, fontWeight: 700, fontFamily: theme.display, letterSpacing: -0.2 }}>{title}</div>
        <div style={{ fontSize: 12, color: theme.dim, marginTop: 2 }}>{sub}</div>
      </div>
      <div style={{ color: theme.dim, fontSize: 18 }}>›</div>
    </div>
  );

  return (
    <PhoneCanvas theme={theme}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 4 }}>
        <IconBtn theme={theme} size={36}>{Icon.back(14)}</IconBtn>
        <h1 style={{
          margin: 0, fontFamily: theme.display, fontSize: 22, fontWeight: 700,
          letterSpacing: -0.5,
          textTransform: theme === THEMES.stage ? 'uppercase' : 'none',
        }}>Add a song</h1>
      </div>

      <div style={{ fontSize: 13, color: theme.dim, lineHeight: 1.5 }}>
        Drop in sheet music or pick a track. We'll detect tempo and align the playhead automatically.
      </div>

      {/* big drop zone */}
      <div style={{
        background: theme.surface, borderRadius: theme.radius,
        border: `2px dashed ${theme.line}`,
        padding: '32px 16px',
        display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10,
        textAlign: 'center', marginTop: 4,
      }}>
        <div style={{
          width: 56, height: 56, borderRadius: theme === THEMES.stage ? 6 : 28,
          background: theme.accent, color: theme === THEMES.studio ? '#0a0a0c' : '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>{Icon.upload(22, 'currentColor')}</div>
        <div style={{ fontSize: 16, fontFamily: theme.display, fontWeight: 700, letterSpacing: -0.3 }}>
          Drop file here
        </div>
        <div style={{ fontSize: 12, color: theme.dim, fontFamily: theme.mono }}>PDF · PNG · JPG · up to 20MB</div>
      </div>

      <div style={{ fontSize: 11, fontFamily: theme.mono, letterSpacing: 1.5, color: theme.dim, textTransform: 'uppercase', marginTop: 4 }}>
        Or pick a source
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {opt(Icon.camera(20), 'Take a photo', 'Snap sheet music with your camera', theme.accent2 || theme.accent)}
        {opt(Icon.file(20), 'Choose PDF or image', 'Pick from your files', null)}
        {opt(Icon.spotify(20, '#1DB954'), 'Connect Spotify', 'Drum along to tracks you\'re playing', null)}
      </div>

      {/* recently imported */}
      <div style={{ fontSize: 11, fontFamily: theme.mono, letterSpacing: 1.5, color: theme.dim, textTransform: 'uppercase', marginTop: 8 }}>
        Recently imported
      </div>
      <div style={{
        background: theme.surface, borderRadius: theme.radius, padding: 12,
        border: theme === THEMES.stage ? `2px solid ${theme.text}` : 'none',
        display: 'flex', alignItems: 'center', gap: 10,
      }}>
        <div style={{
          width: 32, height: 32, borderRadius: 8, background: theme.barColor,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: theme.dim,
        }}>{Icon.file(14)}</div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 13, fontWeight: 600 }}>yyz_drum_chart.pdf</div>
          <div style={{ fontSize: 11, color: theme.dim, fontFamily: theme.mono }}>Processing · 2 of 4 pages</div>
        </div>
        <div style={{ fontSize: 11, fontFamily: theme.mono, color: theme.accent, fontWeight: 700 }}>62%</div>
      </div>
    </PhoneCanvas>
  );
}

// ─────────────────────────────────────────────────────────────
// PLAYER — the main reading view (hero)
// ─────────────────────────────────────────────────────────────
function PlayerScreen({ theme, song = DEFAULT_SONG, currentSlot, playing, onTogglePlay }) {
  // Highlight a couple of drum-hit indicators near current slot
  return (
    <div style={{
      width: '100%', height: '100%', background: theme.bg, color: theme.text,
      fontFamily: theme.body, display: 'flex', flexDirection: 'column',
      overflow: 'hidden',
    }}>
      {/* top bar */}
      <div style={{ padding: '12px 16px 8px', display: 'flex', alignItems: 'center', gap: 10 }}>
        <IconBtn theme={theme} size={34}>{Icon.back(13)}</IconBtn>
        <div style={{ flex: 1, minWidth: 0, textAlign: 'center' }}>
          <div style={{ fontSize: 10, fontFamily: theme.mono, color: theme.dim, letterSpacing: 1.5, textTransform: 'uppercase' }}>Now reading</div>
          <div style={{ fontSize: 15, fontWeight: 700, fontFamily: theme.display, letterSpacing: -0.3, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {song.title}
          </div>
        </div>
        <IconBtn theme={theme} size={34}>{Icon.more(13)}</IconBtn>
      </div>

      {/* big BPM + bar indicator */}
      <div style={{ padding: '4px 16px 6px', display: 'flex', alignItems: 'baseline', gap: 16, justifyContent: 'center' }}>
        <BpmReadout theme={theme} bpm={song.bpm} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
          <div style={{ fontSize: 9, fontFamily: theme.mono, color: theme.dim, letterSpacing: 1.4, textTransform: 'uppercase' }}>Bar</div>
          <div style={{ fontSize: 22, fontFamily: theme.display, fontWeight: 700, fontVariantNumeric: 'tabular-nums', letterSpacing: -1, lineHeight: 1 }}>
            {Math.floor(currentSlot / 16) + 1}<span style={{ color: theme.dim, fontWeight: 400 }}>/{song.bars.length}</span>
          </div>
        </div>
      </div>

      {/* sheet music — the focal point */}
      <div style={{
        flex: 1, minHeight: 0, margin: '4px 12px 0',
        background: theme.surface, borderRadius: theme.radius,
        border: theme === THEMES.stage ? `2px solid ${theme.text}` : `1px solid ${theme.line}`,
        padding: '18px 10px 10px',
        overflow: 'hidden', position: 'relative',
      }}>
        <DrumStaffStack
          bars={song.bars} currentSlot={currentSlot}
          slotsPerBar={16} barsPerLine={2}
          colors={theme.staffColors}
          width={336}
          lineHeight={86}
          lineGap={2}
        />
        {/* corner labels */}
        <div style={{ position: 'absolute', top: 8, right: 12, fontSize: 9, fontFamily: theme.mono, color: theme.dim, letterSpacing: 1.4, textTransform: 'uppercase' }}>
          {song.timeSig[0]}/{song.timeSig[1]}
        </div>
      </div>

      {/* drum hit dots — what's hitting now */}
      <DrumHitIndicator theme={theme} song={song} currentSlot={currentSlot} />

      {/* transport */}
      <div style={{ padding: '8px 16px 16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 18 }}>
        <button style={btnGhost(theme)}>{Icon.metronome(18)}</button>
        <button style={btnGhost(theme)}>{Icon.stop(14)}</button>
        <button onClick={onTogglePlay} style={{
          width: 64, height: 64, borderRadius: theme === THEMES.stage ? 8 : 32,
          border: 'none', background: theme.accent,
          color: theme === THEMES.studio ? '#0a0a0c' : '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          boxShadow: `0 8px 24px ${theme.accent}55`,
          cursor: 'pointer',
        }}>{playing ? Icon.pause(20, 'currentColor') : Icon.play(20, 'currentColor')}</button>
        <button style={btnGhost(theme)}>{Icon.loop(18)}</button>
        <button style={btnGhost(theme)}>{Icon.speed(18)}</button>
      </div>
    </div>
  );
}

function btnGhost(theme) {
  return {
    width: 42, height: 42, borderRadius: 21,
    border: `1.4px solid ${theme.line}`, background: 'transparent',
    color: theme.text, display: 'flex', alignItems: 'center', justifyContent: 'center',
    cursor: 'pointer',
  };
}

function BpmReadout({ theme, bpm }) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div style={{ fontSize: 9, fontFamily: theme.mono, color: theme.dim, letterSpacing: 1.4, textTransform: 'uppercase' }}>BPM</div>
      <div style={{
        fontSize: 36, fontFamily: theme.display, fontWeight: 800,
        fontVariantNumeric: 'tabular-nums', letterSpacing: -1.5, lineHeight: 1,
        color: theme.accent,
      }}>{bpm}</div>
    </div>
  );
}

function DrumHitIndicator({ theme, song, currentSlot }) {
  // Compute which drums are hitting at the current slot
  const barIdx = Math.floor(currentSlot / 16);
  const slotIdx = currentSlot % 16;
  const slot = (song.bars[barIdx] || [])[slotIdx] || [];
  const map = { k: 'KICK', s: 'SNR', h: 'HH', o: 'OHH', c: 'CRSH', r: 'RIDE', t1: 'TOM1', t2: 'TOM2', t3: 'FLR' };
  const chips = ['k','s','h','c','r','t1','t2','t3'];
  return (
    <div style={{ padding: '0 12px 4px', display: 'flex', gap: 6, justifyContent: 'center', flexWrap: 'wrap' }}>
      {chips.map((tok) => {
        const active = slot.includes(tok);
        return (
          <div key={tok} style={{
            padding: '4px 8px',
            background: active ? theme.accent : theme.chip,
            color: active ? (theme === THEMES.studio ? '#0a0a0c' : '#fff') : theme.dim,
            fontFamily: theme.mono, fontSize: 9, fontWeight: 700, letterSpacing: 0.8,
            borderRadius: theme === THEMES.stage ? 2 : 6,
            transition: 'background .08s, color .08s',
          }}>{map[tok]}</div>
        );
      })}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// SONG DETAIL / SETTINGS
// ─────────────────────────────────────────────────────────────
function SongDetailScreen({ theme, song = DEFAULT_SONG }) {
  const row = (label, value, hint) => (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '14px 16px', borderBottom: `1px solid ${theme.line}`,
    }}>
      <div>
        <div style={{ fontSize: 13, fontWeight: 600 }}>{label}</div>
        {hint && <div style={{ fontSize: 11, color: theme.dim, marginTop: 2 }}>{hint}</div>}
      </div>
      <div style={{ fontFamily: theme.mono, fontSize: 13, color: theme.accent, fontWeight: 700 }}>{value}</div>
    </div>
  );
  return (
    <PhoneCanvas theme={theme} padding={0}>
      {/* cover hero */}
      <div style={{
        height: 200, background: `linear-gradient(135deg, ${theme.coverGrad[0]}, ${theme.coverGrad[1]})`,
        position: 'relative', padding: 16,
        display: 'flex', flexDirection: 'column', justifyContent: 'flex-end',
        color: '#fff',
      }}>
        {/* diagonal stripes overlay */}
        <svg style={{ position: 'absolute', inset: 0, opacity: 0.18 }} preserveAspectRatio="none" viewBox="0 0 400 200">
          {Array.from({length: 18}).map((_, i) => (
            <line key={i} x1={-100 + i * 30} y1={0} x2={0 + i * 30} y2={200} stroke="#fff" strokeWidth={2} />
          ))}
        </svg>
        <div style={{ position: 'absolute', top: 12, left: 12 }}>
          <IconBtn theme={{...theme, line: 'rgba(255,255,255,0.45)', text: '#fff'}} size={34}>{Icon.back(13)}</IconBtn>
        </div>
        <div style={{ position: 'absolute', top: 12, right: 12 }}>
          <IconBtn theme={{...theme, line: 'rgba(255,255,255,0.45)', text: '#fff'}} size={34}>{Icon.more(13)}</IconBtn>
        </div>
        <div style={{ position: 'relative', zIndex: 1 }}>
          <div style={{ fontSize: 10, fontFamily: theme.mono, letterSpacing: 1.4, textTransform: 'uppercase', opacity: 0.85 }}>
            {song.bars.length} bars · 4/4
          </div>
          <h1 style={{ margin: '4px 0 0', fontFamily: theme.display, fontSize: 26, fontWeight: 800, letterSpacing: -0.8, lineHeight: 1.05 }}>
            {song.title}
          </h1>
          <div style={{ fontSize: 13, opacity: 0.9, marginTop: 2 }}>{song.artist}</div>
        </div>
      </div>

      {/* play CTA */}
      <div style={{ padding: '14px 16px', display: 'flex', gap: 10 }}>
        <button style={{
          flex: 1, padding: '14px 18px',
          background: theme.btnPrimary, color: theme.btnPrimaryText,
          border: 'none', borderRadius: theme.radius,
          fontFamily: theme.display, fontSize: 14, fontWeight: 700, letterSpacing: 0.4,
          textTransform: theme === THEMES.stage ? 'uppercase' : 'none',
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
        }}>{Icon.play(13)} Start reading</button>
        <button style={{
          padding: '14px 14px',
          background: 'transparent', color: theme.text,
          border: `1.5px solid ${theme.line}`, borderRadius: theme.radius,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>{Icon.speed(16)}</button>
      </div>

      {/* settings */}
      <div style={{ fontSize: 11, fontFamily: theme.mono, letterSpacing: 1.5, color: theme.dim, textTransform: 'uppercase', padding: '8px 16px 0' }}>
        Playback
      </div>
      {row('Tempo', `${song.bpm} BPM`, '5 BPM under original')}
      {row('Count-in', '1 bar', 'Click before playback starts')}
      {row('Metronome', 'On · soft', null)}
      {row('Drum kit', 'Acoustic — Studio', null)}
      {row('Mute', 'Hi-hat', 'Practice the muted part live')}

      <div style={{ fontSize: 11, fontFamily: theme.mono, letterSpacing: 1.5, color: theme.dim, textTransform: 'uppercase', padding: '16px 16px 0' }}>
        Source
      </div>
      {row('Imported', 'PDF · 4 pages', '12 May 2026')}
      {row('Tempo detection', 'Auto · ±2 BPM', null)}

      <div style={{ height: 24 }} />
    </PhoneCanvas>
  );
}

// ─────────────────────────────────────────────────────────────
// PRACTICE — slow-then-speed-up mode
// ─────────────────────────────────────────────────────────────
function PracticeScreen({ theme, song = DEFAULT_SONG }) {
  const startBpm = 72;
  const targetBpm = 116;
  const currentBpm = 92;
  const loops = 8;
  const currentLoop = 3;
  const progress = (currentBpm - startBpm) / (targetBpm - startBpm);

  return (
    <PhoneCanvas theme={theme}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 4 }}>
        <IconBtn theme={theme} size={36}>{Icon.back(14)}</IconBtn>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 10, fontFamily: theme.mono, color: theme.dim, letterSpacing: 1.5, textTransform: 'uppercase' }}>Practice mode</div>
          <h1 style={{ margin: 0, fontFamily: theme.display, fontSize: 22, fontWeight: 700, letterSpacing: -0.5,
            textTransform: theme === THEMES.stage ? 'uppercase' : 'none' }}>
            {theme === THEMES.warm ? (
              <><span style={{ fontFamily: theme.serif, fontStyle: 'italic', fontWeight: 400 }}>Speed</span> ramp</>
            ) : 'Speed ramp'}
          </h1>
        </div>
      </div>

      {/* big BPM */}
      <div style={{
        background: theme.surface, borderRadius: theme.radius, padding: 18,
        border: theme === THEMES.stage ? `2px solid ${theme.text}` : 'none',
        textAlign: 'center', position: 'relative', overflow: 'hidden',
      }}>
        <div style={{ fontSize: 10, fontFamily: theme.mono, color: theme.dim, letterSpacing: 1.5, textTransform: 'uppercase' }}>
          Current tempo
        </div>
        <div style={{
          fontSize: 72, fontFamily: theme.display, fontWeight: 800,
          fontVariantNumeric: 'tabular-nums', letterSpacing: -3.5, lineHeight: 1,
          color: theme.accent, margin: '4px 0',
        }}>{currentBpm}</div>
        <div style={{ fontSize: 12, fontFamily: theme.mono, color: theme.dim, letterSpacing: 1, textTransform: 'uppercase' }}>
          BPM · Loop {currentLoop} of {loops}
        </div>
        {/* range visual */}
        <div style={{ marginTop: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, fontFamily: theme.mono, color: theme.dim }}>
            <span>{startBpm}</span>
            <span style={{ color: theme.accent }}>{currentBpm}</span>
            <span>{targetBpm}</span>
          </div>
          <div style={{ height: 6, background: theme.barColor, borderRadius: 3, overflow: 'hidden', marginTop: 4, position: 'relative' }}>
            <div style={{ width: `${progress * 100}%`, height: '100%', background: theme.barFill }} />
            <div style={{
              position: 'absolute', left: `${progress * 100}%`, top: -3,
              width: 12, height: 12, borderRadius: 6, background: theme.accent,
              transform: 'translateX(-50%)',
              boxShadow: `0 0 0 4px ${theme.bg}`,
            }} />
          </div>
        </div>
      </div>

      {/* loop pattern preview */}
      <div style={{
        background: theme.surface, borderRadius: theme.radius, padding: '14px 10px 10px',
        border: theme === THEMES.stage ? `2px solid ${theme.text}` : `1px solid ${theme.line}`,
      }}>
        <div style={{ fontSize: 11, fontFamily: theme.mono, color: theme.dim, letterSpacing: 1.5, textTransform: 'uppercase', padding: '0 6px 8px' }}>
          Looping bars 1–2
        </div>
        <DrumStaff bars={[BAR_GROOVE, BAR_GROOVE_2]} barIndex={0} barCount={2}
          currentBeat={6} showClef={true} colors={theme.staffColors}
          width={332} height={86} />
      </div>

      {/* loop dots */}
      <div style={{ display: 'flex', gap: 6, justifyContent: 'center', padding: '0 4px' }}>
        {Array.from({length: loops}).map((_, i) => (
          <div key={i} style={{
            flex: 1, height: 8, borderRadius: theme === THEMES.stage ? 1 : 4,
            background: i < currentLoop ? theme.accent : theme.barColor,
            opacity: i === currentLoop - 1 ? 1 : (i < currentLoop ? 0.6 : 1),
          }} />
        ))}
      </div>

      {/* config rows */}
      <div style={{
        background: theme.surface, borderRadius: theme.radius, overflow: 'hidden',
        border: theme === THEMES.stage ? `2px solid ${theme.text}` : `1px solid ${theme.line}`,
      }}>
        <Config theme={theme} label="Start tempo" value={`${startBpm} BPM`} />
        <Config theme={theme} label="Target tempo" value={`${targetBpm} BPM`} />
        <Config theme={theme} label="Loops" value={`${loops}`} />
        <Config theme={theme} label="Step" value="+6 BPM / loop" last />
      </div>

      {/* big start */}
      <button style={{
        marginTop: 4, padding: '16px 18px',
        background: theme.btnPrimary, color: theme.btnPrimaryText,
        border: 'none', borderRadius: theme.radius,
        fontFamily: theme.display, fontSize: 15, fontWeight: 800, letterSpacing: 0.5,
        textTransform: theme === THEMES.stage ? 'uppercase' : 'none',
        display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
      }}>{Icon.play(14)} Resume from loop {currentLoop}</button>
    </PhoneCanvas>
  );
}

function Config({ theme, label, value, last }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '13px 16px',
      borderBottom: last ? 'none' : `1px solid ${theme.line}`,
    }}>
      <div style={{ fontSize: 13, fontWeight: 500 }}>{label}</div>
      <div style={{ fontFamily: theme.mono, fontSize: 13, color: theme.accent, fontWeight: 700 }}>{value}</div>
    </div>
  );
}

Object.assign(window, {
  THEMES,
  HomeScreen, UploadScreen, PlayerScreen, SongDetailScreen, PracticeScreen,
  Icon, CoverArt, PhoneCanvas, IconBtn,
});
