package dev.hoennascension;

import com.mojang.brigadier.Command;
import dev.hoennascension.config.HoennConfig;
import dev.hoennascension.core.DonationTracker;
import dev.hoennascension.core.MessageRouter;
import dev.hoennascension.core.MessageStore;
import dev.hoennascension.core.SpawnLocator;
import dev.hoennascension.core.Wondertrade;
import dev.hoennascension.ui.CommsScreen;
import dev.hoennascension.ui.HoennConfigScreen;
import dev.hoennascension.ui.HudOverlay;
import dev.hoennascension.ui.MinimizedPill;
import dev.hoennascension.ui.PopupManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class HoennAscensionClient implements ClientModInitializer {
    public static final String MOD_ID = "hoennascension";

    public static final MessageStore STORE = new MessageStore();
    public static final PopupManager POPUPS = new PopupManager();

    public static KeyBinding OPEN;
    public static KeyBinding ACCEPT;
    public static KeyBinding DENY;

    /** Cuenta de ticks para guardar el historial cada 30 s (por si el juego se cierra mal). */
    private static int saveTicks = 0;

    @Override
    public void onInitializeClient() {
        HoennConfig cfg = HoennConfig.get();
        STORE.load();
        // Una sola vez: re-clasifica el historial guardado con la estructura actual de canales
        if (cfg.historyVersion < 3) {
            STORE.reclassifyAll();
            cfg.historyVersion = 3;
            cfg.save();
            STORE.save();
        }

        String cat = "category." + MOD_ID;
        OPEN = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hoennascension.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, cat));
        ACCEPT = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hoennascension.accept", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, cat));
        DENY = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hoennascension.deny", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, cat));

        // Captura de mensajes: los del juego/servidor y los del chat de jugadores. Con el mod desactivado no se toca nada.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
                overlay || MessageRouter.accept(message, false));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, timestamp) ->
                MessageRouter.accept(params.applyChatDecoration(message), true));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HoennConfig c = HoennConfig.get();
            while (OPEN.wasPressed()) {
                if (client.player == null) continue;
                // Con el mod desactivado, la tecla abre los ajustes para poder volver a activarlo
                client.setScreen(c.enabled ? new CommsScreen() : new HoennConfigScreen(null));
            }
            if (c.enabled) {
                POPUPS.tick(client);
                DonationTracker.tick();
                Wondertrade.tick();
                SpawnLocator.tick();
            }
            if (++saveTicks >= 600) {
                saveTicks = 0;
                STORE.saveIfDirty();
            }
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (!HoennConfig.get().enabled) return;
            HudOverlay.render(ctx);
            POPUPS.render(ctx);
            MinimizedPill.renderHud(ctx);
        });

        // La pastilla minimizada y las notificaciones también se dibujan y se pulsan sobre cualquier otra
        // pantalla (chat, inventario, menú de pausa...) donde el ratón esté libre.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof CommsScreen || screen instanceof HoennConfigScreen) return;
            MinimizedPill.cancel();
            ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, tickDelta) -> {
                MinimizedPill.dragTo(mouseX, mouseY);
                MinimizedPill.renderOnScreen(ctx, mouseX, mouseY);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) -> {
                if (!HoennConfig.get().enabled) return true;
                // Botones de las notificaciones (aceptar / rechazar una solicitud)
                if (button == 0 && POPUPS.click(mouseX, mouseY)) return false;
                if (button == 0 && MinimizedPill.press(mouseX, mouseY)) return false;
                // Clic derecho sobre la pastilla = cerrarla del todo
                if (button == 1 && MinimizedPill.hit(mouseX, mouseY)) {
                    MinimizedPill.dismiss();
                    return false;
                }
                return true;
            });
            ScreenMouseEvents.allowMouseRelease(screen).register((s, mouseX, mouseY, button) ->
                    !(button == 0 && MinimizedPill.release()));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            STORE.save();
            POPUPS.clear();
            MinimizedPill.unsnooze();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> STORE.save());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("hoennascension")
                        .then(ClientCommandManager.literal("reload").executes(ctx -> {
                            HoennConfig.reload();
                            ctx.getSource().sendFeedback(Text.translatable("hoennascension.cmd.reloaded"));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("clear").executes(ctx -> {
                            STORE.clear();
                            ctx.getSource().sendFeedback(Text.translatable("hoennascension.cmd.cleared"));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("toggle").executes(ctx -> {
                            HoennConfig c = HoennConfig.get();
                            c.enabled = !c.enabled;
                            c.save();
                            ctx.getSource().sendFeedback(Text.translatable(
                                    c.enabled ? "hoennascension.cmd.on" : "hoennascension.cmd.off"));
                            return Command.SINGLE_SUCCESS;
                        }))));
    }

    /** orb | levelup | none */
    public static void playSound(String id) {
        if (id == null) return;
        SoundEvent ev = switch (id) {
            case "orb" -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "levelup" -> SoundEvents.ENTITY_PLAYER_LEVELUP;
            default -> null;
        };
        if (ev == null) return;
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(ev, 1.0f));
    }
}