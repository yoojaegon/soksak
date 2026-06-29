# Reel Theory

## Overview
Reel Theory is a design system for film studios, screening platforms, art-house streaming services, and any product that treats cinema as a medium rather than as content. The aesthetic borrows from film-festival print catalogs and 35mm projection booths: deep ink black, champagne gold for credits and accolades, a precise crimson reserved for play state and live screening indicators, and a faint film-grain texture beneath surfaces to give the interface the analog warmth of celluloid. Layouts are letterboxed — content lives within deliberate horizontal bands with black bars above and below, echoing the projected frame. Hero trailers play full-bleed at full brightness; metadata recedes into the gold-on-black palette of a Cannes program. Reel Theory rejects the streaming-service convention of dense thumbnail grids in favor of a curated, editorial film-card hierarchy where each title gets room to be a film, not a tile.

## Colors
- **Background** (#0B0B0C): Ink black — the dominant surface
- **Surface** (#141416): Card and panel surface, marginally lifted from background
- **Surface Elevated** (#1C1C1F): Hover and active surfaces, modal background
- **Surface Highest** (#262629): Active film card during playback
- **Letterbox** (#000000): Pure black used for top and bottom letterbox bars
- **Champagne** (#D4A574): Primary accent — title credits, accolades, primary CTAs
- **Champagne Bright** (#E8C094): Hover state on champagne
- **Champagne Deep** (#A88554): Pressed state
- **Champagne Soft** (#3A2E1F): Champagne-tinted backgrounds for active items
- **Crimson** (#A6192E): Reserved exclusively for play state, "Now Playing," live screening indicator
- **Crimson Bright** (#C8334B): Hover on crimson
- **Text Primary** (#F5F0E6): Warm off-white reminiscent of projector light
- **Text Secondary** (#A8A299): Descriptions, cast lists, secondary credits
- **Text Subtle** (#6E6962): Director, year, runtime, language metadata
- **Text Faint** (#3F3C37): Disabled, placeholder
- **Outline** (#252528): Card edges, divider lines
- **Outline Strong** (#3D3D40): Focused inputs, prominent separators
- **Critic Score Hot** (#A6192E): 90-100 rotten-tomato-equivalent score
- **Critic Score Warm** (#D4A574): 70-89 score
- **Critic Score Cool** (#6E6962): Below 70

## Typography
- **Display Serif**: GT Sectra (fallback Tiempos Headline, Trajan Pro) — used for film titles, hero typography, opening credits
- **Modern Serif**: Source Serif 4 (fallback Charter) — used for editorial body, synopses
- **Sans**: Söhne (fallback Inter, Helvetica Neue) — used for UI, navigation, metadata
- **Display Condensed**: Druk Wide or Druk Condensed — used for the title of "REEL THEORY" wordmark and high-impact callouts
- **Mono**: JetBrains Mono — used for runtimes, year, aspect ratios, timecodes

The signature is the GT Sectra display serif at very large sizes — its high contrast and tall x-height evoke the typography of mid-century film posters. Film titles render at 88-144px in display serif. Director and credit lines use sans uppercase caption with 0.16em tracking — a direct reference to film festival program typography. Runtime and year are always in mono ("1h 47min · 2026 · 1.85:1") so the metadata bar aligns cleanly. Synopses use Source Serif italic at 21px for the headline pull and 18px regular for body.

Type scale: Cinematic 144/140 (display serif, -0.04em, used once per page on the hero film), Title 88/88 (display serif), Subtitle 56/60 (display serif), H1 40/48, H2 28/36 (display serif), Credit Line 12/18 (sans uppercase 0.16em tracking), Body Pull 21/32 (modern serif italic), Body 18/30 (modern serif), Small 14/22 (sans), Caption 12/16 (sans uppercase 0.12em tracking), Mono Meta 13/16 (mono tabular), Wordmark 14/16 (Druk Wide uppercase 0.2em tracking).

## Elevation
Reel Theory uses surface lightness for elevation, layered with subtle film-grain texture. Each surface step adds approximately 8% brightness. Cards on background use surface (#141416); hover lifts to surface-elevated. Active "Now Playing" cards gain a 1px crimson border with no glow — the crimson itself is enough. Modals use surface-elevated with a 1px champagne-soft border (rgba(212, 165, 116, 0.16)) and a backdrop of rgba(11, 11, 12, 0.9).

A signature treatment: a faint film-grain SVG noise overlay (8% opacity, 1px noise) sits across the entire viewport, lending the interface an analog warmth without distracting from content. Hero trailer playbacks remove the grain for crisp video presentation.

Border radius: 0 on letterbox bars and film stills (sharp editorial crops), 2px on metadata pills, 4px on buttons, 6px on cards, 8px on modals. Rounded radius is intentionally restrained — cinema is rectangular.

## Components
- **Hero Letterboxed Frame**: The signature container for featured films. A 21:9 cinematic frame with hard black letterbox bars above and below. Film title in display serif 144px sits in the lower-left of the frame. Director credit in sans caption uppercase champagne sits below. A small crimson "PLAY TRAILER" button hovers in the lower-right.
- **Film Card**: Editorial card showing a 2:3 vertical poster (sharp corners, no border-radius) with the film title in display serif 28px below, year and runtime in mono caption, and a champagne accolade row beneath ("CANNES · 2025 · OFFICIAL SELECTION"). Hover lifts the card with a subtle 240ms champagne accent stripe sliding in from the left.
- **Now Playing Indicator**: A persistent banner appearing at the bottom of the viewport during active playback. Crimson 4px top rule, surface-highest background. Contains: 48px poster thumbnail, film title in display serif 18px, sans-caption director credit, scrubber bar in champagne with crimson playhead, and timecode in mono on the right.
- **Credits Block**: A typographic block listing cast and crew in film-poster format — director, screenplay, cinematography, editor, music — each label in sans caption uppercase 0.16em tracking champagne, value in display serif 18px text-primary. Used on film detail pages and as the closing element of trailers.
- **Accolade Strip**: A horizontal row of laurel-wreath ornaments (gold) bracketing accolade text. Used to highlight festival selections, critic scores, and awards. The laurel is a 24px SVG icon on each side with champagne stroke.
- **Critic Score Plate**: A square 64px plate showing critic score in display serif tabular figures, with the score color-coded (hot crimson 90+, warm champagne 70-89, cool subtle below 70). Used in film cards and detail pages.
- **Trailer Player**: A 16:9 or 2.39:1 video container with a 64px crimson play button centered. Hover scales the play button to 72px and brightens to crimson-bright. Custom controls appear on hover: timeline scrubber in champagne, play/pause toggle in champagne, time remaining in mono.
- **Letterbox Page Wrapper**: A page-level layout component that wraps the entire experience in a 6px black letterbox top and bottom (32px on larger viewports). Used selectively on hero pages to invoke a projected-frame feel.
- **Synopsis Pull**: A long-form synopsis block with a 21px modern-serif italic deck (the "logline") at the top, a 4px champagne left rule, and 18px modern-serif body beneath. Set in a 580px reading column.
- **Buttons**: Primary uses champagne (#D4A574) fill with ink black text in sans uppercase caption with 0.12em tracking, 4px radius, 12x24px padding. Hover brightens to champagne-bright. Secondary uses transparent background with 1px champagne border. The crimson play button is reserved exclusively for media playback — never used as a generic CTA.
- **Watchlist / Saved**: A small bookmark icon in outline champagne by default, filling to solid champagne on tap. Used to save films for later — paired with a subtle 200ms fill animation.
- **Showtime / Screening Block**: For platforms with theatrical or live-screening data — a list of upcoming screenings with venue name in display serif 19px, location and time in sans caption uppercase, and a champagne "RESERVE" CTA on the right. Sold-out states show "SOLD OUT" in mono uppercase ink-faint.
- **Filter Bar**: Sticky horizontal bar with sans caption filter labels: GENRE, ERA, DIRECTOR, COUNTRY, LANGUAGE. Active filters use champagne fill on a 2px-radius pill. The bar background uses surface with a 1px outline bottom border.
- **Inputs**: 1px outline-strong border, surface background, 4px radius, 12x16px padding. Focus border becomes champagne with no glow.

## Spacing
- Base unit: 4px
- Scale: 4, 8, 16, 24, 32, 48, 64, 96, 128, 192, 256px
- Container max-width: 1440px with 48px horizontal padding (large viewports get more breathing room)
- Reading column: 580px for synopsis and editorial
- Letterbox bar height: 24px on smaller viewports, 32px on desktop, 48px on cinematic pages
- Card grid gap: 32px horizontal, 48px vertical
- Section spacing: 96px between major sections

## Motion
Motion in Reel Theory is unhurried and projector-paced. Standard duration is 320ms with cubic-bezier(0.22, 0.61, 0.36, 1) easing (a confident ease-out). Card hovers use a slow 320ms champagne accent slide. Film posters cross-fade between frames every 4.8 seconds in autoplay hero contexts. Trailer modal open uses a 480ms scale-up from the poster origin to fullscreen — like a projector unspooling. The film-grain overlay subtly shifts at 12 fps (intentionally slow) to mimic actual film grain. Scrubbing the trailer timeline scrubs the grain too. Page transitions use a slow letterbox-bar slide-in (320ms) before the body content fades in (240ms).

## Iconography
Custom outlined icon set at 1.5px stroke, sharp and geometric. The vocabulary is cinematic: film reel, clapboard, ticket stub, popcorn, projector, marquee bulb, camera body, mono earpiece, vinyl reel, theater seat. Laurel wreath SVGs serve as accolade brackets. Icons are 18px default in text-secondary, scaling to 24px on primary controls and 32px in feature contexts. Champagne on active states, crimson only on playback controls.

## Photography & Video Guidance
Posters use 2:3 vertical ratio — original film-poster proportions. Hero stills use 21:9 cinematic ratio. Trailers default to 1.85:1 or 2.39:1 aspect. Avoid square crops, modern-streaming-tile aspect ratios, and any treatment that crops out original poster typography. Black-and-white poster art is welcomed and presented at full fidelity. Behind-the-scenes photography uses 3:2 with a faint sepia warmth.

## Voice and Tone
- Editorial and considered. Like a film festival catalog or a Criterion Collection essay.
- Use cinematic idiom: "Directed by," "Director of Photography," "Edited by," "Original Score," "In Selection," "Restored from a 4K scan."
- Avoid streaming-service marketing copy ("You'll love this!" — never).
- Synopses are loglines first, then a single editorial paragraph — not bullet lists of genre tags.
- Errors and empty states use cinematic dignity: "This title is not currently available in your region," "No screenings scheduled."
- Loading states use a slow champagne radial mark — like a film reel spinning up.

## Do's and Don'ts
- Do use letterboxing as both decorative and structural — the projected frame is a core metaphor
- Do reserve crimson strictly for playback indicators — Now Playing, play buttons, live screening
- Do use sans caption uppercase with wide tracking (0.12-0.16em) for all credit and metadata typography
- Do allow film posters to retain their original aspect ratio (2:3) and typography
- Do pair the film-grain overlay subtly with all dark surfaces — the analog warmth is intentional
- Don't use streaming-service tile grids — Reel Theory's grid is curated and editorial
- Don't apply crimson to non-playback buttons or generic CTAs
- Don't crop posters into square thumbnails or modern-streaming card ratios
- Don't autoplay trailers with sound — silent autoplay is acceptable on hover, sound requires intent
- Don't introduce neon colors, gradients, or modern fintech aesthetics
- Don't use exclamation marks in copy, marketing CTAs, or push notifications
- Don't reduce display serif headlines below 56px — the cinematic scale is the system's voice
