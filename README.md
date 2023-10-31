# RGML Quilt

The mod you knew you never wanted.

## What is this?

RGML Quilt, Risugami Quilt, Risugami's ModLoader Loader, or some other name, is a mod that wraps mods designed for
Risugami's ModLoader. It uses Quilt's Loader Plugins feature to perform this in a way that works with mods that may
want to depend on ModLoader.

## Usage

1. Download the mod and ensure that the version you have matches that of Minecraft.
2. Put the mod in your mods folder.
3. Add `-Dloader.experimental.allow_loading_plugins=true` to your JVM arguments.
4. Add any RGML mods to your mods folder, ensuring that the file extension is `.zip`.
5. Launch the game.
6. Submit an issue if something doesn't work. It's likely that it won't. Very little testing has been done.

## Will [insert popular mod here] work?

- **Is it a Quilt mod?** Yes. Quilt supports its own mods.
- **Is it a Fabric mod?** Yes. Quilt supports Fabric mods natively.
- **Is it Forge** (itself, not a mod needing it)? As long as you have a version matching that of Minecraft (and it's actually
  a version designed on top of ModLoader), it should work.
- **Is it a Forge mod?** Probably. Any mod that does not replace any Minecraft classes should work. Mods that do
  are hit-and-miss, depending on what they replace and what other mods you have installed.
  ~~hey look the important question has been answered~~
- **Is it a ModLoader mod?** Look at Forge. Since Forge (in these early versions) is just an extensive API wrapping
  ModLoader, it should work. However, if it replaces any Minecraft classes, watch out!
- **Is it some other kind of mod?** What? Why would that work in the first place?

## Wait, Forge mods?

Yes, if you actually read that text above, you would have seen that Forge works and so do its mods.
This particular build actually has some code in it to fix some conflicts with itself and Forge since Forge decided
it would be a _great_ idea to overwrite some classes that ModLoader also overwrites.
