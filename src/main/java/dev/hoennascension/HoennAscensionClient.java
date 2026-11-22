package dev.hoennascension;

import com.mojang.brigadier.Command;
import dev.hoennascension.config.HoennConfig;
import dev.hoennascension.core.DonationTracker;
import dev.hoennascension.core.MessageRouter;
import dev.hoennascension.core.MessageStore;
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

    @Override
    public void onInitializeClient() {
        HoennConfig.get();
        STORE.load();

        String cat = "category." + MOD_ID;
        OPEN = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hoennascension.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, cat));
        ACCEPT = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hoennascension.accept", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, cat));
        DENY = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hoennascension.deny", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, cat));

        // Captura de mensajes (mensajes de sistema/plugins y chat de jugadores)
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
                overlay || MessageRouter.accept(message));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, timestamp) ->
                MessageRouter.accept(params.applyChatDecoration(message)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN.wasPressed()) {
                if (client.player != null) client.setScreen(new CommsScreen());
            }
            POPUPS.tick(client);
            DonationTracker.tick();
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            HudOverlay.render(ctx);
            POPUPS.render(ctx);
            MinimizedPill.renderHud(ctx);
        });

        // La pastilla minimizada también se dibuja y se puede pulsar sobre cualquier otra pantalla
        // (chat, inventario, menú de pausa...) donde el ratón esté libre.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof CommsScreen || screen instanceof HoennConfigScreen) return;
            MinimizedPill.cancel();
            ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, tickDelta) -> {
                MinimizedPill.dragTo(mouseX, mouseY);
                MinimizedPill.renderOnScreen(ctx, mouseX, mouseY);
            });
            // Pulsar y soltar sin mover = maximizar; pulsar, arrastrar y soltar = mover la pastilla.
            ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) -> {
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