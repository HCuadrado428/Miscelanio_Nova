---
name: gui-screens
description: Pantallas y GUIs en Minecraft 1.20.1–1.21.11 - menús con inventario (AbstractContainerMenu + Screen), pantallas simples, abrirlas desde un bloque y registrarlas en Fabric, NeoForge y Forge. Usar para cualquier interfaz.
---

# GUIs y pantallas

## Dos tipos
1. **Pantalla simple** (sin inventario, solo cliente): `extends Screen`. Se abre con `Minecraft.getInstance().setScreen(new MiPantalla())`.
2. **Menú con inventario** (máquina, cofre…): `AbstractContainerMenu` (lógica, servidor+cliente) + `AbstractContainerScreen<M>` (dibujo, solo cliente) + `MenuType` registrado.

## Piezas de un menú con inventario
| Pieza | Dónde | Notas |
|---|---|---|
| `MyMenu extends AbstractContainerMenu` | común | slots, `quickMoveStack` (shift-click), `stillValid` |
| `MenuType<MyMenu>` | registro por loader | ver tabla de abajo |
| `MyScreen extends AbstractContainerScreen<MyMenu>` | **solo cliente** | `renderBg` con `GuiGraphics.blit(...)` |
| Registro menú → pantalla | **solo cliente** | ver tabla |
| Abrirlo | servidor, en `useWithoutItem` / `use` del bloque | `player.openMenu(provider)` |

## Por loader
| | Registrar `MenuType` con datos extra | Asociar pantalla | Abrir con datos extra |
|---|---|---|---|
| **Fabric** | `new ExtendedScreenHandlerType<>(factory, codec)` (Fabric API; en 1.20.1 sin codec) | `MenuScreens.register(TYPE, MyScreen::new)` en `ClientModInitializer` | `player.openMenu(ExtendedScreenHandlerFactory)` |
| **NeoForge** | `IMenuTypeExtension.create((id, inv, buf) -> ...)` | `RegisterMenuScreensEvent` → `event.register(TYPE, MyScreen::new)` | `player.openMenu(provider, buf -> buf.writeBlockPos(pos))` |
| **Forge 1.20.1** | `IForgeMenuType.create((id, inv, buf) -> ...)` | `MenuScreens.register(...)` dentro de `FMLClientSetupEvent#enqueueWork` | `NetworkHooks.openScreen(serverPlayer, provider, pos)` |

Sin datos extra basta `new MenuType<>(MyMenu::new, FeatureFlags.VANILLA_SET)`.

## Dibujo según versión
- 1.20+: todo con `GuiGraphics` (`blit`, `drawString`, `fill`).
- **1.21.2+**: `blit` pide un `RenderType`/función de render como primer argumento (`RenderType::guiTextured`); en 1.21.6+ cambia a `RenderPipelines.GUI_TEXTURED`. **Comprueba la firma exacta en las fuentes de la versión**.
- Texturas de GUI: `assets/<modid>/textures/gui/container/<nombre>.png`, normalmente 256×256 con el área usada arriba a la izquierda (`imageWidth`=176, `imageHeight`=166 como un cofre).

## Reglas
- Nunca importes clases de pantalla (`net.minecraft.client.*`) desde código común o de servidor: crashea el servidor dedicado. Usa clases separadas registradas solo en el cliente.
- Sincroniza datos numéricos (progreso, energía) con `ContainerData` + `addDataSlots`.
- Prueba con `runServer` + `runClient` además de un solo jugador.
- Para la textura de la GUI usa la skill `pixel-art-16x16` (tamaño libre, potencia de 2).
