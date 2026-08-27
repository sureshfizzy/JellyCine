# Vela brand assets

`Vela` is the proposed replacement codename for Vela. The name is short, works across languages, and avoids tying the client to either Jellyfin or Emby.

The mark combines three product ideas:

- a sail for moving through a personal media library across devices;
- a forward play symbol for playback;
- a projection beam and wave for cinema and continuity.

## Palette

- Near black: `#05070B`
- Cobalt: `#1565C0`
- Sky blue: `#64B5F6`
- Violet accent: `#7C4DFF`

## Files

- `vela-app-icon.png`: opaque 1024 px master app icon, suitable for iOS export and legacy Android launchers.
- `vela-mark.png`: transparent generated brand mark for splash, headers, About, and README.
- `vela-mark.svg`: simplified vector master for deterministic scaling.
- `vela-mark-monochrome.svg`: single-color mark for small or themed surfaces.
- `previews/`: small-size checks on light and dark backgrounds.

The generated PNGs were created with the built-in image generation tool. The SVGs are simplified production counterparts so launcher and notification use does not depend on raster glow details.

Android-ready resources live as `vela_launcher*` and `vela_logo.png` under `shared/src/androidMain/res`. `ic_launcher` / `ic_launcher_round` now point at those drawables; density mipmaps are generated from `vela-app-icon.png`.
