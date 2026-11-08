# Plan: Port OpenComputers from MC 1.12.2 to MC 1.21.1 (NeoForge)

## Context
The current target is `master-MC1.12`: OC 1.8.10, Forge 14.23.5, ForgeGradle 5, MCP `stable_39` mappings, Java 8, and Scala 2.11, which Forge bundled. There are about 790 Scala files and 214 Java files; the Java includes the public `li.cil.oc.api`. The `master-MC1.16` branch doesn't help. Despite its name it's an old 1.12.2 snapshot (OC 1.7.5), so the port starts from 1.12.2 with no midway reference.

**Decisions made:** target **NeoForge 21.1.x**. **Keep Scala**, upgraded to 2.13. Port the **core first**, then add back JEI, Jade, CC: Tweaked and AE2, and optionally Mekanism.

This jump crosses three major breaks:
- the 1.13 "flattening" (no metadata, tags replace the ore dictionary, datapacks);
- the 1.17 rendering rewrite (no fixed-function GL);
- the 1.20.5 change from item NBT to data components.

Any intermediate MC version would be throwaway work, so the plan goes straight to 1.21.1. It's sequenced so the game launches as early as possible, then features come back subsystem by subsystem.

## Branch setup
- Reset `claude/sharp-cray-wj63mo` to `origin/master-MC1.12`. It's currently based on 1.7.10 and has no unique work. Do all port work there.
- Expect the old code to fail to compile for a long time. Working in phases (below) and committing per subsystem keeps it reviewable.

## Phase 0: Build toolchain
- Gradle 8.10+ wrapper, **ModDevGradle 2.x** (`net.neoforged.moddev`), Java 21 toolchain, and the `scala` plugin on **Scala 2.13.x**.
- Mappings: official Mojang names plus Parchment (`2024.11.17` for 1.21.1).
- Rewrite `build.gradle` and `build.properties`:
  - Remove `fancyGradle`, `fg.deobf`, the coremod manifest attributes (`FMLCorePlugin`, `FMLAT`) and the `replaceSourceTokensScala` hack. Inject the version through `neoforge.mods.toml` expansion instead.
  - Ship the Scala library with **jarJar**, because NeoForge loads mods as JPMS modules and doesn't provide Scala. Check first whether a maintained 1.21.1 Scala language provider exists. If not, use a thin Java `@Mod` entrypoint class that hands off to Scala `object`s. This is the safer default.
  - Keep embedding `OC-LuaJ`, `OC-JNLua` and `OC-JNLua-Natives`, plus Typesafe Config, inside the mod jar. Check for split packages with other modules.
- Replace `mcmod.info` with `META-INF/neoforge.mods.toml`. Move `oc_at.cfg` to `META-INF/accesstransformer.cfg` and rewrite its entries in Mojang names, keeping only those still needed.
- Tests: move to scalatest 3.2 and JUnit 5. Keep `src/test/scala/InternetFilteringRuleTest.scala`.

## Phase 1: Mechanical renames (do this first, all at once)
- Build a rename table from MCP names to Mojang names and apply it with a script (sed or scalafix):
  - `TileEntity` → `BlockEntity`, `NBTTagCompound` → `CompoundTag`, `World` → `Level`, `EntityPlayer` → `Player`
  - `IBlockState` → `BlockState`, `EnumFacing` → `Direction`, `ItemStack.isEmpty`, `world.isRemote` → `level.isClientSide`, `ResourceLocation.fromNamespaceAndPath`
- Change package imports from `net.minecraftforge.fml.*` to `net.neoforged.*`.
- Scala 2.11 → 2.13:
  - `JavaConversions` → `scala.jdk.CollectionConverters`
  - `mapValues` and `view` semantics changed
  - procedure syntax `def f() {` becomes `def f(): Unit =`
  - `Traversable` and `TraversableOnce` → `Iterable` and `IterableOnce`
  - Run the Scalafix `ProcedureSyntax` and `Collection213Upgrade` rules.
- Stub out or exclude `integration/*` except `minecraft`, `minecraftforge` and `opencomputers`, so the core compiles.

## Phase 2: Removing the coremod
- Delete `common/launch/TransformerLoader.scala`, `CoreModContainer.scala` and `common/asm/ClassTransformer.scala`. NeoForge has no LaunchWrapper coremods.
- `SimpleComponent` injection, which `ClassTransformer` does with the templates in `java/li/cil/oc/common/asm/template/`, becomes a runtime wrapper. Write a driver that turns a `BlockEntity` implementing `SimpleComponent` into a `ManagedEnvironment` with a reflective `@Callback` bridge (the same idea as `ManagedTileEntityEnvironment.java`). Expose it via the Adapter or through a capability. Tell addon authors that injection is gone.
- `@Optional.Interface` stripping is dropped. Isolate integrations behind `ModList.isLoaded` checks instead.
- Mixins only if one turns out to be truly needed (none are expected).

## Phase 3: Registration and the flattening
- Move to `DeferredRegister` for blocks, items, block entity types, menu types, entity types, sound events, recipe serializers, creative tabs and data components. Rewrite `common/init/Blocks.scala`, `Items.scala` and `CreativeTab.scala`.
- **Split `common/item/Delegator`** (the metadata multi-item, about 105 files involved) into one `Item` per former subtype with a flat registry name (for example `opencomputers:cpu1`). `api.Items` / `ItemInfo` lookups keep working by name.
- Blocks: 1.12 `IBlockState` properties go to `BlockState` properties. The `blockstates/*.json` and `models/*` get regenerated in 1.21 format, ideally through datagen providers in `src/main/scala/.../datagen`.
- Ore dictionary → **tags** (79 metadata and OreDict references). Add OC tags under `data/opencomputers/tags` and use `c:` common tags (`c:ingots/iron` and so on).
- Lang: `.lang` → `lang/*.json`, with lowercase locale file names. Write a one-off conversion script.
- World height is now -64..320. Audit geolyzer, robot, drone and `BlockPosition` code for 0–255 assumptions.

## Phase 4: Item data (data components)
- OC keeps a lot in item NBT: drive and FS addresses, EEPROM code, robot and drone info, tablet data, prints, colors.
- Add typed `DataComponentType`s with codecs and stream codecs for the main ones (e.g. `oc:data`, `oc:color`, `oc:print_data`). Fall back to `CustomData` for the opaque OC `data` compound, so `api.driver.item.*` `dataTag` still behaves the same.
- Update `common/item/data/*` (`DriveData`, `RobotData`, `PrintData`, `TabletData`, ...) to read and write components.

## Phase 5: Block entities, ticking, saving, syncing
- `tileentity/*` plus its traits: give each one a `BlockEntityType`. `ITickable` → `BlockEntityTicker` via `getTicker`. `readFromNBT`/`writeToNBT` → `loadAdditional`/`saveAdditional(tag, HolderLookup.Provider)`.
- Client sync goes through `getUpdatePacket`/`getUpdateTag` instead of the custom description packets.
- Keep the `api.network` node model (`Environment`, `SidedEnvironment`). Connectivity lookup in `server/network/Network.scala` stays the same, but neighbour discovery goes through **NeoForge block capabilities**: register an `oc:environment` capability, plus `Capabilities.EnergyStorage`, `ItemHandler` and `FluidHandler` in `RegisterCapabilitiesEvent`.
- Power: Forge Energy only. Drop the IC2, BuildCraft and RF converters from `integration/`. Keep the `PowerConverter` FE path.
- Robots: `FakePlayer` from `net.neoforged.neoforge.common.util`. Rework `server/agent/Player.scala` against the new `ServerPlayer` and interaction APIs. This is one of the hardest areas.
- Drone: `EntityType` plus `SynchedEntityData`.

## Phase 6: Networking
- Keep OC's byte-buffer packet protocol (`common/PacketBuilder`, `client/PacketHandler`, `server/PacketHandler`). Wrap it in **one** `CustomPacketPayload` per direction, carrying `PacketType` and raw bytes, registered via `RegisterPayloadHandlersEvent`. That keeps the change to the roughly 60 packet types small.
- Replace `SimpleNetworkWrapper`/`FMLEventChannel` usage in about 6 files.

## Phase 7: Menus and GUIs
- `GuiHandler` IDs become `MenuType`s with `IMenuTypeExtension` and extra data via `openMenu(provider, buf -> ...)`.
- `common/container/*` → `AbstractContainerMenu`, and `client/gui/*` (about 33 files) → `AbstractContainerScreen` with `GuiGraphics`. Widgets → `AbstractWidget`/`Button.builder`.
- Keyboard input: `charTyped`/`keyPressed` with GLFW key codes. The `KeyboardKeys` mapping from LWJGL2 to OC codes needs a GLFW translation table.

## Phase 8: Rendering (largest client effort)
- About 68 files use `GL11`, `GlStateManager` or `Tessellator`. Rewrite them to `PoseStack`, `MultiBufferSource`/`VertexConsumer` and custom `RenderType`s. No immediate mode, no display lists.
- **Screen text** (`client/renderer/font/*`, `TextBufferRenderCache`, `gui/BufferRenderer`):
  - The display-list cache becomes a `VertexBuffer` (or a dynamic texture) rebuilt when dirty.
  - Glyph atlas → `DynamicTexture`; `font.hex` parsing stays.
  - Needs a custom shader/RenderType for the background color and glyph quads.
- Block entity renderers (`renderer/tileentity/*`) → `BlockEntityRenderer`, registered in `EntityRenderersEvent.RegisterRenderers`.
- Custom baked models (`CableModel`, `ScreenModel`, `PrintModel`, `RobotModel`, `ServerRackModel`, `NetSplitterModel`, `DroneModel`) → `BakedModel` plus `ModelData`, through a geometry loader in `ModelEvent.RegisterGeometryLoaders`, or `ModelEvent.ModifyBakingResult`.
- Holograms, the pet renderer, the highlight/MFU renderer → `RenderLevelStageEvent`.
- The manual (`client/renderer/markdown/*`) → `GuiGraphics`. The `OreDictImageProvider` becomes a tag image provider.

## Phase 9: Recipes, loot, worldgen, config
- OC's runtime HOCON recipe loader (`common/recipe/Recipes.scala`, `recipes/*.recipes`) can't register recipes at runtime anymore. Recommended approach:
  - Convert `default.recipes` into **generated datapack JSON** with a one-off converter or datagen.
  - Ship `hardmode`/`peaceful` as built-in datapacks the user can turn on. Drop the `gregtech` set.
  - Port `ColorizeRecipe`, `DecolorizeRecipe`, `LootDiskCyclingRecipe` and `ExtendedShaped/Shapeless` (which keep NBT through crafting) to `CustomRecipe` plus a `RecipeSerializer`, copying components.
- Loot floppies: the filesystem contents stay under `assets/opencomputers/loot/*` (read as resources). Dungeon chest injection moves to a **Global Loot Modifier**.
- Config: keep Typesafe `application.conf` and `Settings.scala`, which preserves behavior and the existing user configs. Move the config file to `config/opencomputers/settings.conf`. Optionally mirror client-only settings to `ModConfigSpec` later.
- Sounds: `sounds.json` mostly carries over. Register `SoundEvent`s.

## Phase 10: Lua architectures
- `server/machine/luac/*` (JNLua natives) and `luaj/*` need few MC-specific changes. Check that the JNI natives load under Java 21 and the NeoForge module classloader (`LuaStateFactory` extracts them to a temp dir), and check for macOS arm64 support.
- `OSAPI`/`PersistenceAPI` only touch `Settings` and the world path. Update the world save path to `server.getWorldPath(LevelResource.ROOT)`.

## Phase 11: API and integrations
- `src/main/java/li/cil/oc/api/**`: update the signatures to Mojang names (e.g. `World` → `Level`, `ItemStack` data → components). Bump the API version to signal the break, and bump OC to 1.9 or 2.0 for 1.21.1.
- Re-add integrations one at a time, each behind `ModList.isLoaded`:
  1. `minecraft`/`minecraftforge` drivers (inventories, fluids, energy via capabilities)
  2. **JEI** 19.x
  3. **Jade** (replaces `waila`)
  4. **CC: Tweaked** 1.113+ (peripheral bridge)
  5. **AE2** 19.x
  6. Mekanism 10.7 (optional)
- Dropped (not on 1.21.1): IC2, BuildCraft, Forestry, ProjectRed, Thaumcraft, Charset, EnderIO, Railcraft, CoFH, WR-CBE, EnderStorage, ExtraCells, TIS-3D. Delete their `integration/*` folders and update `Mods.scala`.

## Milestones (one commit or PR series each)
1. ✅ Toolchain builds an empty NeoForge mod with Scala bundled (Phase 0). See "Milestone 1 notes" below.
2. Core compiles with integrations stubbed (Phases 1–3).
3. Game launches, and blocks and items exist with textures (3–4).
4. A Case, CPU, RAM, EEPROM, HDD, screen and keyboard boot OpenOS (5–8, screen rendering first).
5. Robots, drones, microcontrollers, assembler, printer, server racks, holograms.
6. Recipes, loot and the manual.
7. Integrations.

## Verification
- Run `./gradlew build` at every milestone. `./gradlew test` covers the scalatest suite.
- Run `./gradlew runClient` / `runServer` via ModDevGradle. Smoke test: build a tier-1 computer, boot OpenOS from the loot floppy, run `install`, `edit`, `lua`, `components` and network `ping` between two computers over cable and wireless.
- Add NeoForge **GameTests** (`runGameTestServer`) for headless checks: placing a case and screen forms a network; a machine boots and reaches `computer.uptime() > 0`; adapter-to-inventory driver calls work.
- `runData` datagen produces blockstates, models, lang, tags, recipes and loot modifiers, and the build checks that the output is clean in git.
- Save compatibility from 1.12 isn't in scope. A 1.12 → 1.21 world upgrade isn't realistic. Document this.

## Main risks
- Scala on JPMS/NeoForge (jarJar or a language provider). Prove it in Milestone 1.
- Screen text rendering performance without display lists.
- Robot FakePlayer interactions.
- The breaking API change for third-party addons.

## Milestone 1 notes
- **Toolchain:** Gradle 8.14.3, ModDevGradle 2.0.148, NeoForge 21.1.250, Parchment 2024.11.17, Java 21, Scala 2.13.16. All versions are in `gradle.properties`; `build.properties` is gone.
- **Entry point:** `li.cil.oc.OpenComputersMod` (Java, `@Mod`) hands off to `li.cil.oc.ModBootstrap` (Scala). No Scala language provider is used. Java and Scala are compiled jointly by scalac, with both `src/main/java` and `src/main/scala` in the Scala source set, because the two languages reference each other.
- **Legacy sources:** the 1.12 code stays in place but is left out of compilation. Only paths in `portedSources` in `build.gradle` compile. Grow that list as files are ported, and delete it once everything compiles. Tests are excluded until the code they cover is ported.
- **Bundled libraries:**
  - Scala and Typesafe Config ship as jar-in-jar.
  - The relocated LuaJ, JNLua and JNLua natives are merged into the mod jar, as before.
  - In dev, non-mod libraries must also be on `additionalRuntimeClasspath`, or the game can't see them (`ClassNotFoundException: scala.…`).
- **Verified:**
  - `./gradlew build` passes.
  - `./gradlew runGameTestServer` passes `BootstrapGameTests.scalaRuntimeLoaded`, using the `opencomputers:empty` 1×1×1 template.
  - The built jar loads in a real NeoForge 21.1.250 dedicated server, which starts normally.
- **Known gaps:**
  - `runGameTestServer` exits 0 even when mod loading crashes. CI must grep for `All N required tests passed` until this is fixed.
  - The CurseForge upload (`cursegradle` 1.4.0 doesn't support Gradle 8) has been removed for now. Modrinth publishing is kept.
  - `runClient` hasn't been tried (no display in the dev container).
