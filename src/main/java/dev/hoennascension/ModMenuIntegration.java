package dev.hoennascension;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.hoennascension.ui.HoennConfigScreen;

/** Muestra el botón de configuración del mod en Mod Menu (opcional: solo se carga si Mod Menu está instalado). */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return HoennConfigScreen::new;
    }
}