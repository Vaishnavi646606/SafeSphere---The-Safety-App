# Skill: UI/UX Design System — SafeSphere Admin

## Description
Enforces modern, professional, eye-pleasing UI/UX design across all SafeSphere admin pages.
This skill must be checked EVERY TIME a page is created or modified.

## Design System Rules

### Color Palette (STRICT — use only these)
```
Background:
  Page:        bg-[#0a0a0f]          (near black)
  Card:        bg-[#12121a]          (dark card)
  Card Hover:  bg-[#1a1a2e]          (subtle lift)
  Sidebar:     bg-[#0d0d14]          (slightly different from page)
  Input:       bg-[#1a1a2e]          (dark input fields)
  Modal:       bg-[#12121a]          (same as card)

Text:
  Heading:     text-white
  Body:        text-gray-300
  Muted:       text-gray-500
  Link:        text-emerald-400      (hover: text-emerald-300)

Accent:
  Primary:     emerald-500           (buttons, active states, highlights)
  Success:     emerald-400
  Warning:     amber-400
  Danger:      rose-400
  Info:        sky-400

Borders:
  Default:     border-white/5        (very subtle)
  Hover:       border-white/10
  Active:      border-emerald-500/50

Gradients:
  Card glow:   from-emerald-500/10 to-transparent
  Hero:        from-emerald-600/20 via-transparent to-transparent
```

### Typography
```
Page Title:    text-3xl font-bold text-white tracking-tight
Section Title: text-xl font-semibold text-white
Card Title:    text-sm font-medium text-gray-400 uppercase tracking-wider
Card Value:    text-3xl font-bold text-white
Body:          text-sm text-gray-300
Caption:       text-xs text-gray-500
```

### Spacing System
```
Page Padding:     p-8
Card Padding:     p-6
Card Gap:         gap-6
Section Gap:      space-y-8
Inner Element:    space-y-2 or space-y-4
```

### Card Design
```
Base:    bg-[#12121a] rounded-2xl border border-white/5 p-6
Hover:   hover:border-white/10 transition-all duration-300
Shadow:  shadow-lg shadow-black/20
Glow:    For important cards, add: ring-1 ring-emerald-500/10
```

### Stats Card Pattern
```
┌─────────────────────────────┐
│  icon (40px, rounded-xl,    │
│  bg-emerald-500/10,         │
│  text-emerald-400)          │
│                             │
│  Value    (text-3xl bold)   │
│  Label    (text-sm gray-400)│
│  +12% ↑  (text-xs green)    │
└─────────────────────────────┘
Width: equal columns in grid
Min height: consistent across row
Icon: top-right corner, not inline with text
```

### Table Design
```
Header:    bg-[#0d0d14] text-xs font-medium text-gray-400 uppercase tracking-wider
Row:       border-b border-white/5 hover:bg-white/[0.02] transition-colors
Cell:      px-6 py-4 text-sm text-gray-300
Striping:  NO alternating colors. Use hover instead.
Selected:  bg-emerald-500/5 border-l-2 border-emerald-500
```

### Button Design
```
Primary:   bg-emerald-600 hover:bg-emerald-500 text-white px-4 py-2 rounded-xl
           font-medium transition-all duration-200 shadow-lg shadow-emerald-500/20
Secondary: bg-white/5 hover:bg-white/10 text-gray-300 border border-white/10
Danger:    bg-rose-600/10 hover:bg-rose-600/20 text-rose-400 border border-rose-500/20
Ghost:     hover:bg-white/5 text-gray-400
```

### Chart Design
```
Background:   transparent (no chart background)
Grid:         stroke-white/5 (very subtle)
Line:         stroke-emerald-400 strokeWidth-2
Area Fill:    fill-emerald-500/10
Tooltip:      bg-[#1a1a2e] border border-white/10 rounded-xl shadow-2xl
Axis Text:    fill-gray-500 text-xs
Colors:       ['#10b981', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4']
```

### Sidebar Design
```
Width:        w-64 (256px)
Background:   bg-[#0d0d14]
Border:       border-r border-white/5
Logo Section: p-6, text-xl font-bold
Nav Items:    px-4 py-3 rounded-xl mx-2 text-gray-400
Active Item:  bg-emerald-500/10 text-emerald-400 font-medium
              border-l-2 border-emerald-400 (or left accent)
Hover:        bg-white/5 text-white
User Section: Bottom, border-t border-white/5, p-4
```

### Empty States
```
Center vertically and horizontally in container
Icon: 48px, text-gray-600
Title: text-lg font-medium text-gray-400 mt-4
Subtitle: text-sm text-gray-500 mt-2
Action button (if applicable): mt-4
```

### Loading States
```
Skeleton: bg-white/5 rounded-xl animate-pulse
Match exact dimensions of content being loaded
Cards: 3-4 skeleton cards in same grid
Table: 5-8 skeleton rows
Charts: Single skeleton block matching chart height
```

### Badge/Status Design
```
Rescued:      bg-emerald-500/10 text-emerald-400 border border-emerald-500/20
False Alarm:  bg-amber-500/10 text-amber-400 border border-amber-500/20
No Response:  bg-rose-500/10 text-rose-400 border border-rose-500/20
Triggered:    bg-orange-500/10 text-orange-400 border border-orange-500/20
Call Made:    bg-sky-500/10 text-sky-400 border border-sky-500/20
Call Answered:bg-cyan-500/10 text-cyan-400 border border-cyan-500/20
Active:       bg-emerald-500/10 text-emerald-400
Inactive:     bg-gray-500/10 text-gray-500
```

### Page Layout Pattern
```
Every admin page follows:
1. Page header (title + subtitle + action buttons) — sticky or top
2. Filters/controls row (if needed)
3. Stats cards row (if dashboard-type page)
4. Main content (table or charts)
5. Pagination (if table)

Max content width: max-w-[1600px] mx-auto
```

### Responsive Breakpoints
```
Mobile:  1 column cards, sidebar hidden (hamburger menu)
Tablet:  2 column cards, sidebar collapsed
Desktop: 4 column cards (stats), sidebar full
Wide:    Same as desktop with more breathing room
```

### Animation/Transitions
```
All interactive: transition-all duration-200
Hover scale:     DO NOT use scale transforms on cards
Fade in:         animate-in fade-in duration-300
Page load:       Stagger card animations (delay-75, delay-100, etc.)
```

## Anti-Patterns (NEVER DO THESE)
1. ❌ NEVER use pure black (#000000) as background — use #0a0a0f
2. ❌ NEVER use bright white text on stat values without proper hierarchy
3. ❌ NEVER cram cards together without proper gap
4. ❌ NEVER use different border radius on same page (stick to rounded-2xl)
5. ❌ NEVER show raw UUIDs to user — truncate or hide
6. ❌ NEVER show "undefined" or "null" — show "—" or "N/A"
7. ❌ NEVER leave empty charts blank — show empty state message
8. ❌ NEVER use inline styles — only Tailwind classes
9. ❌ NEVER mix color temperatures (warm + cool on same element)
10. ❌ NEVER put too many stat cards in one row (max 4 per row)

## Reference Designs (Inspiration)
- Vercel Dashboard (dark, minimal, clean)
- Linear App (dark, professional, spacious)
- Raycast (dark, beautiful cards)
- Planetscale Dashboard (data-heavy but clean)

## Verification Checklist
Before approving any page:
- [ ] All colors match the palette above
- [ ] Cards have consistent padding and border radius
- [ ] Text hierarchy is clear (title > subtitle > body > caption)
- [ ] Empty states are handled gracefully
- [ ] Loading states exist for all data
- [ ] Hover effects are subtle and consistent
- [ ] Sidebar active state matches current page
- [ ] No visual clutter — breathing room between elements
- [ ] Icons are from lucide-react, consistent size
- [ ] Page looks good at 1280px, 1440px, and 1920px width

## Changelog
- 2026-04-08 - Initial creation — Design system for SafeSphere Admin
