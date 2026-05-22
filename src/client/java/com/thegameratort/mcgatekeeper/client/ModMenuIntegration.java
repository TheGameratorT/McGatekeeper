package com.thegameratort.mcgatekeeper.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.thegameratort.mcgatekeeper.client.screen.KeyManagementScreen;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return KeyManagementScreen::new;
    }
}
