// drum-staff.jsx — SVG drum-tab renderer with moving playhead
//
// A "song" is an array of bars. Each bar is an array of subdivision slots.
// Each slot is an array of drum tokens: 'k' kick, 's' snare, 'h' hihat,
// 'c' crash, 'r' ride, 't1' high tom, 't2' mid tom, 't3' floor tom,
// 'o' open hihat, '_' rest.

// ─────────────────────────────────────────────────────────────
// Default song — a basic rock groove, then a fill (8 bars total)
// 16 slots per bar = 16th notes; pattern uses every other slot for 8ths.
// ─────────────────────────────────────────────────────────────
const BAR_GROOVE = [
  ['k','h'],[],['h'],[],   ['s','h'],[],['h'],[],
  ['k','h'],[],['k','h'],[], ['s','h'],[],['h'],[],
];
const BAR_GROOVE_2 = [
  ['k','h'],[],['h'],[],   ['s','h'],[],['h'],[],
  ['k','h'],[],['h'],[],   ['s','h'],[],['s','h'],[],
];
const BAR_FILL = [
  ['k','h'],[],['h'],[],   ['s','h'],[],['h'],[],
  ['s'],['s'],['t1'],['t1'], ['t2'],['t2'],['t3'],['t3'],
];
const BAR_CRASH = [
  ['k','c'],[],['h'],[],   ['s','h'],[],['h'],[],
  ['k','h'],[],['k','h'],[], ['s','h'],[],['h'],[],
];

const DEFAULT_SONG = {
  title: 'Smells Like Teen Spirit',
  artist: 'Nirvana',
  bpm: 116,
  timeSig: [4, 4],
  bars: [BAR_CRASH, BAR_GROOVE, BAR_GROOVE_2, BAR_FILL, BAR_CRASH, BAR_GROOVE, BAR_GROOVE_2, BAR_FILL],
};

// ─────────────────────────────────────────────────────────────
// DrumStaff — renders one or many bars in a horizontal row.
//
// props:
//   bars: array of bar patterns
//   barIndex: which bar index to start at (for windowed view)
//   barCount: how many bars to show
//   currentBeat: float 0..barCount*16 (subdivisions in shown window)
//   showClef: render percussion clef + time sig at left
//   colors: { line, note, accent, playhead, dim }
//   width, height
// ─────────────────────────────────────────────────────────────
function DrumStaff({
  bars, barIndex = 0, barCount = 2, currentBeat = 0,
  showClef = true, colors, width = 300, height = 100,
  slotsPerBar = 16, beatsPerBar = 4, showPlayhead = true,
  staffPaddingX = 12,
}) {
  const c = {
    line: '#1a1612',
    note: '#1a1612',
    accent: '#ff2a3d',
    playhead: '#ff2a3d',
    dim: 'rgba(0,0,0,0.25)',
    barLine: '#1a1612',
    ...(colors || {}),
  };

  // Layout: staff rows are 5 evenly-spaced horizontal lines.
  // y0..y4 top→bottom. Note positions reference these.
  const top = 28;
  const lineGap = 9;
  const y = [0,1,2,3,4].map((i) => top + i * lineGap);
  const staffBottom = y[4];
  const staffTop = y[0];

  // Drum vertical positions
  const yOf = {
    h: staffTop - 12,           // hihat above top line
    o: staffTop - 12,           // open hihat (same y, drawn with circle)
    c: staffTop - 20,           // crash even higher
    r: staffTop - 16,           // ride
    t1: y[1] - lineGap / 2,     // high tom (top space)
    t2: y[2] - lineGap / 2,     // mid tom (above middle)
    s:  y[2],                   // snare on middle line
    t3: y[3] + lineGap / 2,     // floor tom (bottom space)
    k:  y[4] + lineGap,         // kick below staff
  };

  const clefW = showClef ? 32 : 0;
  const innerX0 = clefW + staffPaddingX;
  const innerX1 = width - staffPaddingX;
  const innerW = innerX1 - innerX0;

  const slotsTotal = slotsPerBar * barCount;
  const slotW = innerW / slotsTotal;
  const barW = slotW * slotsPerBar;

  const slotX = (s) => innerX0 + (s + 0.5) * slotW;

  // Bars window
  const window = bars.slice(barIndex, barIndex + barCount);

  // ────────────── render helpers
  const noteOval = (cx, cy, fill = c.note, key) => (
    <ellipse key={key} cx={cx} cy={cy} rx={4.2} ry={3.1} fill={fill}
      transform={`rotate(-22 ${cx} ${cy})`} />
  );
  const noteX = (cx, cy, stroke = c.note, key) => (
    <g key={key} stroke={stroke} strokeWidth={1.6} strokeLinecap="round">
      <line x1={cx - 4} y1={cy - 4} x2={cx + 4} y2={cy + 4} />
      <line x1={cx - 4} y1={cy + 4} x2={cx + 4} y2={cy - 4} />
    </g>
  );
  const openHat = (cx, cy, stroke = c.note, key) => (
    <g key={key} stroke={stroke} strokeWidth={1.6} fill="none">
      <line x1={cx - 4} y1={cy - 4} x2={cx + 4} y2={cy + 4} strokeLinecap="round"/>
      <line x1={cx - 4} y1={cy + 4} x2={cx + 4} y2={cy - 4} strokeLinecap="round"/>
      <circle cx={cx} cy={cy - 8} r={2.8} />
    </g>
  );

  // Generate per-slot rendered elements
  const elements = [];
  window.forEach((bar, bi) => {
    bar.forEach((slot, si) => {
      if (!slot || slot.length === 0) return;
      const globalSlot = bi * slotsPerBar + si;
      const x = slotX(globalSlot);
      slot.forEach((tok, ti) => {
        const yy = yOf[tok];
        if (yy === undefined) return;
        const key = `${bi}-${si}-${tok}-${ti}`;
        if (tok === 'h') elements.push(noteX(x, yy, c.note, key));
        else if (tok === 'o') elements.push(openHat(x, yy, c.note, key));
        else if (tok === 'c' || tok === 'r') elements.push(noteX(x, yy, c.accent, key));
        else elements.push(noteOval(x, yy, c.note, key));
      });
    });
  });

  // Stems + beams for hihat groups (every slot with 'h' or 'o' or 'c' or 'r' gets a stem going up)
  // Group hihats into 8th-note beams (every 2 slots)
  const upStems = [];
  const downStems = [];
  window.forEach((bar, bi) => {
    bar.forEach((slot, si) => {
      if (!slot || slot.length === 0) return;
      const globalSlot = bi * slotsPerBar + si;
      const x = slotX(globalSlot);
      const hasTop = slot.some((t) => 'hocr'.includes(t));
      const hasMid = slot.some((t) => 's t1 t2 t3'.split(' ').includes(t));
      const hasKick = slot.includes('k');
      if (hasTop) {
        const topY = Math.min(...slot.filter((t) => 'hocr'.includes(t)).map((t) => yOf[t]));
        upStems.push({ x, y1: topY, y2: topY - 16, bi, si });
      } else if (hasMid && !hasKick) {
        const midY = slot.includes('s') ? yOf.s : yOf[slot.find((t) => 't1 t2 t3'.split(' ').includes(t))];
        upStems.push({ x, y1: midY, y2: midY - 22, bi, si });
      }
      if (hasKick) {
        downStems.push({ x, y1: yOf.k, y2: yOf.k + 16 });
      }
    });
  });

  // Beam: connect consecutive up-stems whose slots are adjacent and same beat (every 2 slots = 1 beam group)
  const beamGroups = [];
  let cur = [];
  upStems.forEach((s, i) => {
    if (cur.length === 0) cur.push(s);
    else {
      const last = cur[cur.length - 1];
      const sameBar = s.bi === last.bi;
      const consecutive = sameBar && s.si - last.si <= 2 && Math.floor(last.si / 4) === Math.floor(s.si / 4);
      if (consecutive) cur.push(s);
      else { if (cur.length > 1) beamGroups.push(cur); cur = [s]; }
    }
    if (i === upStems.length - 1 && cur.length > 1) beamGroups.push(cur);
  });

  // Playhead position
  const phX = innerX0 + (currentBeat / slotsTotal) * innerW;
  const playheadVisible = showPlayhead && currentBeat >= 0 && currentBeat <= slotsTotal;

  return (
    <svg viewBox={`0 0 ${width} ${height}`} width={width} height={height}
      style={{ display: 'block', overflow: 'visible' }}>
      {/* staff lines */}
      {y.map((yy, i) => (
        <line key={i} x1={clefW} y1={yy} x2={width} y2={yy} stroke={c.line} strokeWidth={0.9} />
      ))}

      {/* percussion clef + time sig */}
      {showClef && (
        <g>
          <rect x={clefW - 12} y={staffTop} width={4} height={staffBottom - staffTop} fill={c.line} />
          <rect x={clefW - 6} y={staffTop} width={1.5} height={staffBottom - staffTop} fill={c.line} />
          <text x={clefW + 2} y={staffTop + 16} fontFamily="Georgia, serif" fontWeight={700} fontSize={17} fill={c.line}>
            {beatsPerBar}
          </text>
          <text x={clefW + 2} y={staffBottom - 1} fontFamily="Georgia, serif" fontWeight={700} fontSize={17} fill={c.line}>
            4
          </text>
        </g>
      )}

      {/* bar lines */}
      {window.map((_, bi) => (
        <line key={'bl'+bi} x1={innerX0 + bi * barW} y1={staffTop} x2={innerX0 + bi * barW} y2={staffBottom} stroke={c.barLine} strokeWidth={0.9} />
      ))}
      {/* final bar line */}
      <line x1={innerX1} y1={staffTop} x2={innerX1} y2={staffBottom} stroke={c.barLine} strokeWidth={0.9} />

      {/* stems */}
      {upStems.map((s, i) => (
        <line key={'us'+i} x1={s.x + 4} y1={s.y1} x2={s.x + 4} y2={s.y2} stroke={c.note} strokeWidth={1.1} />
      ))}
      {downStems.map((s, i) => (
        <line key={'ds'+i} x1={s.x - 4} y1={s.y1} x2={s.x - 4} y2={s.y2} stroke={c.note} strokeWidth={1.1} />
      ))}

      {/* beams */}
      {beamGroups.map((g, i) => {
        const x1 = g[0].x + 4;
        const x2 = g[g.length - 1].x + 4;
        const yy = Math.min(...g.map(s => s.y2));
        return <rect key={'bm'+i} x={x1} y={yy} width={x2 - x1} height={2.5} fill={c.note} />;
      })}

      {/* notes (drawn after stems so they sit on top) */}
      {elements}

      {/* playhead */}
      {playheadVisible && (
        <g>
          <line x1={phX} y1={staffTop - 22} x2={phX} y2={staffBottom + 14}
            stroke={c.playhead} strokeWidth={2.2} strokeLinecap="round" />
          <circle cx={phX} cy={staffTop - 24} r={3} fill={c.playhead} />
        </g>
      )}
    </svg>
  );
}

// ─────────────────────────────────────────────────────────────
// DrumStaffStack — multi-line score with bars wrapping vertically.
// barsPerLine bars per line; playhead jumps to the active line.
// ─────────────────────────────────────────────────────────────
function DrumStaffStack({
  bars, currentSlot = 0, slotsPerBar = 16, barsPerLine = 2,
  colors, width = 320, lineHeight = 86, lineGap = 4,
}) {
  const totalBars = bars.length;
  const lines = Math.ceil(totalBars / barsPerLine);
  const currentBar = Math.floor(currentSlot / slotsPerBar);
  const currentLineIdx = Math.floor(currentBar / barsPerLine);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: lineGap }}>
      {Array.from({ length: lines }).map((_, li) => {
        const startBar = li * barsPerLine;
        const lineBars = bars.slice(startBar, startBar + barsPerLine);
        const isCurrent = li === currentLineIdx;
        const lineStartSlot = startBar * slotsPerBar;
        const lineEndSlot = (startBar + lineBars.length) * slotsPerBar;
        const localSlot = isCurrent ? currentSlot - lineStartSlot : -1;
        return (
          <div key={li} style={{
            opacity: isCurrent ? 1 : 0.55,
            transition: 'opacity .25s',
          }}>
            <DrumStaff
              bars={lineBars}
              barIndex={0}
              barCount={lineBars.length}
              slotsPerBar={slotsPerBar}
              currentBeat={localSlot}
              showPlayhead={isCurrent}
              showClef={li === 0}
              colors={colors}
              width={width}
              height={lineHeight}
            />
          </div>
        );
      })}
    </div>
  );
}

Object.assign(window, { DrumStaff, DrumStaffStack, DEFAULT_SONG, BAR_GROOVE, BAR_GROOVE_2, BAR_FILL, BAR_CRASH });
