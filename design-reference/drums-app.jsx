// drums-app.jsx — main mount: design canvas with 3 visual directions × 5 screens
// Player screens are LIVE — the playhead moves in real time at the song BPM.
// Tweaks panel lets the user override accent color and font family globally.

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "accentOverride": "violet",
  "fontOverride": "default",
  "showPostits": true
}/*EDITMODE-END*/;

const ACCENT_OPTIONS = {
  default: null,
  red:     '#ff2a3d',
  orange:  '#ff6a14',
  cyan:    '#1ad1c8',
  violet:  '#8a3dff',
  pink:    '#ff0066',
};
const ACCENT_HEXES = Object.values(ACCENT_OPTIONS).filter(Boolean);

const FONT_OPTIONS = {
  default: null,
  grotesk: {
    display: '"Space Grotesk", system-ui, sans-serif',
    body:    '"Space Grotesk", system-ui, sans-serif',
    mono:    '"JetBrains Mono", monospace',
    label:   'Space Grotesk',
  },
  heavy: {
    display: '"Archivo Black", "Archivo", system-ui, sans-serif',
    body:    '"Archivo", system-ui, sans-serif',
    mono:    '"DM Mono", monospace',
    label:   'Archivo Black',
  },
  bricolage: {
    display: '"Bricolage Grotesque", system-ui, sans-serif',
    body:    '"Bricolage Grotesque", system-ui, sans-serif',
    mono:    '"DM Mono", monospace',
    label:   'Bricolage',
  },
};

// ─────────────────────────────────────────────────────────────
// useLivePlayhead — moves currentSlot forward at the song BPM
// ─────────────────────────────────────────────────────────────
function useLivePlayhead(song, playing) {
  const [slot, setSlot] = React.useState(0);
  React.useEffect(() => {
    if (!playing) return;
    const slotsPerBeat = 4;          // 16th-note slots
    const slotsPerBar = song.bars[0].length;
    const totalSlots = slotsPerBar * song.bars.length;
    const msPerSlot = (60_000 / song.bpm) / slotsPerBeat;
    let last = performance.now();
    let raf;
    const tick = (t) => {
      const elapsed = t - last;
      last = t;
      setSlot((s) => {
        const next = s + (elapsed / msPerSlot);
        return next >= totalSlots ? 0 : next;
      });
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [playing, song]);
  return [slot, setSlot];
}

function LivePlayer({ theme, song = DEFAULT_SONG, initiallyPlaying = true }) {
  const [playing, setPlaying] = React.useState(initiallyPlaying);
  const [slot] = useLivePlayhead(song, playing);
  return (
    <PlayerScreen theme={theme} song={song}
      currentSlot={slot}
      playing={playing}
      onTogglePlay={() => setPlaying((p) => !p)} />
  );
}

// ─────────────────────────────────────────────────────────────
// Apply tweaks to a theme
// ─────────────────────────────────────────────────────────────
function applyTweaks(theme, tweaks) {
  let t = { ...theme };
  const accentHex = tweaks.accentOverride && tweaks.accentOverride !== 'default'
    ? ACCENT_OPTIONS[tweaks.accentOverride]
    : null;
  if (accentHex) {
    t.accent = accentHex;
    t.staffColors = { ...t.staffColors, accent: accentHex, playhead: accentHex };
    t.barFill = accentHex;
    t.btnPrimary = accentHex;
    t.btnPrimaryText = '#fff';
    t.coverGrad = [accentHex, t.coverGrad[1]];
  }
  const font = tweaks.fontOverride && tweaks.fontOverride !== 'default'
    ? FONT_OPTIONS[tweaks.fontOverride]
    : null;
  if (font) {
    t.display = font.display;
    t.body = font.body;
    t.mono = font.mono;
  }
  return t;
}

function AndroidArtboard({ theme, children, dark }) {
  return (
    <AndroidDevice
      width={372} height={760}
      dark={dark ?? theme.statusDark}
    >
      {children}
    </AndroidDevice>
  );
}

function DirectionSection({ id, theme, tweaks }) {
  const t = React.useMemo(() => applyTweaks(theme, tweaks), [theme, tweaks]);
  const aw = 372, ah = 760;
  return (
    <DCSection id={id} title={`${theme.name}`}
      subtitle={subtitleFor(theme)}>
      <DCArtboard id={`${id}-home`} label="01 · Home" width={aw} height={ah}>
        <AndroidArtboard theme={t}><HomeScreen theme={t} /></AndroidArtboard>
      </DCArtboard>
      <DCArtboard id={`${id}-upload`} label="02 · Upload" width={aw} height={ah}>
        <AndroidArtboard theme={t}><UploadScreen theme={t} /></AndroidArtboard>
      </DCArtboard>
      <DCArtboard id={`${id}-player`} label="03 · Player (live ●)" width={aw} height={ah}>
        <AndroidArtboard theme={t}><LivePlayer theme={t} /></AndroidArtboard>
      </DCArtboard>
      <DCArtboard id={`${id}-detail`} label="04 · Song detail" width={aw} height={ah}>
        <AndroidArtboard theme={t}><SongDetailScreen theme={t} /></AndroidArtboard>
      </DCArtboard>
      <DCArtboard id={`${id}-practice`} label="05 · Practice" width={aw} height={ah}>
        <AndroidArtboard theme={t}><PracticeScreen theme={t} /></AndroidArtboard>
      </DCArtboard>
    </DCSection>
  );
}

function subtitleFor(theme) {
  if (theme === THEMES.studio) return 'Dark · electric red · Space Grotesk. After-hours studio energy.';
  if (theme === THEMES.stage)  return 'Cream + ink · Archivo Black · brutal posters & oversized type.';
  if (theme === THEMES.warm)   return 'Warm paper · terracotta · Bricolage Grotesque + Instrument Serif.';
  return '';
}

// ─────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────
function App() {
  const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);

  // For TweakColor: it works on the hex value directly. Map current
  // override key → hex, then map selected hex → key on change.
  const currentAccentHex = ACCENT_OPTIONS[t.accentOverride] || '__none__';
  const onAccentChange = (hex) => {
    const key = Object.entries(ACCENT_OPTIONS).find(([k, v]) => v === hex)?.[0] || 'default';
    setTweak('accentOverride', key);
  };

  return (
    <>
      <DesignCanvas>
        <DirectionSection id="studio" theme={THEMES.studio} tweaks={t} />
        <DirectionSection id="stage"  theme={THEMES.stage}  tweaks={t} />
        <DirectionSection id="warm"   theme={THEMES.warm}   tweaks={t} />

        {t.showPostits && (
          <DCPostIt top={-30} left={80} rotate={-3} width={260}>
            Three visual directions for the drumming app. Each shows the full flow: Home → Upload → Player → Song detail → Practice. The Player is <b>live</b> — playhead moves in real time at the song BPM. Tap any artboard to focus; ←/→ to step through.
          </DCPostIt>
        )}
        {t.showPostits && (
          <DCPostIt top={-10} right={80} rotate={2} width={220}>
            Use Tweaks ↗ to override accent + font across all three directions.
          </DCPostIt>
        )}
      </DesignCanvas>

      <TweaksPanel title="Tweaks">
        <TweakSection label="Accent color">
          <TweakColor label="Override" value={currentAccentHex}
            options={ACCENT_HEXES}
            onChange={onAccentChange} />
          <TweakButton label="Reset to theme defaults" secondary
            onClick={() => setTweak('accentOverride', 'default')} />
        </TweakSection>

        <TweakSection label="Font family">
          <TweakSelect label="Override" value={t.fontOverride}
            options={Object.keys(FONT_OPTIONS).map((k) => ({
              value: k,
              label: k === 'default' ? 'Theme default' : FONT_OPTIONS[k].label,
            }))}
            onChange={(v) => setTweak('fontOverride', v)} />
        </TweakSection>

        <TweakSection label="Canvas">
          <TweakToggle label="Show design notes" value={t.showPostits}
            onChange={(v) => setTweak('showPostits', v)} />
        </TweakSection>
      </TweaksPanel>
    </>
  );
}

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(<App />);
