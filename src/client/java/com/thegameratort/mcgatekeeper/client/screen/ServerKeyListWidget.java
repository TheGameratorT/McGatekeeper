package com.thegameratort.mcgatekeeper.client.screen;

import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import com.thegameratort.mcgatekeeper.client.auth.ClientKeyStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.BiConsumer;

public class ServerKeyListWidget extends AlwaysSelectedEntryListWidget<ServerKeyListWidget.KeyEntry> {

    public ServerKeyListWidget(MinecraftClient client, int x, int y, int width, int height, int itemHeight) {
        super(client, width, height, y, itemHeight);
        setX(x);
    }

    @Override
    public int getRowWidth() {
        return getWidth() - 36;
    }

    public void populate(List<ClientKeyStore.KeyEntry> entries, BiConsumer<ClientKeyStore.KeyEntry, Boolean> onRemove) {
        clearEntries();
        for (ClientKeyStore.KeyEntry entry : entries) {
            addEntry(new KeyEntry(entry, onRemove));
        }
        if (!children().isEmpty()) {
            setSelected(children().get(0));
        }
    }

    public static class KeyEntry extends AlwaysSelectedEntryListWidget.Entry<KeyEntry> {
        private static final int BTN_SIZE = 14;
        private static final int PAD = 5;
        private static final Identifier CROSS_BTN = Identifier.ofVanilla("widget/cross_button");
        private static final Identifier CROSS_BTN_HOV = Identifier.ofVanilla("widget/cross_button_highlighted");

        private final ClientKeyStore.KeyEntry data;
        private final BiConsumer<ClientKeyStore.KeyEntry, Boolean> onRemove;
        private final String address;
        private final String keyLabel;

        KeyEntry(ClientKeyStore.KeyEntry data, BiConsumer<ClientKeyStore.KeyEntry, Boolean> onRemove) {
            this.data = data;
            this.onRemove = onRemove;
            this.address = data.lastKnownAddress();
            this.keyLabel = "Server: " + Ed25519Util.fingerprint(data.serverKeyB64())
                    + "  →  Client: " + Ed25519Util.fingerprint(data.clientPublicKeyB64());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            int bx = x + w - BTN_SIZE - PAD;
            int by = y + (h - BTN_SIZE) / 2;
            boolean btnHov = hovered && mouseX >= bx && mouseX < bx + BTN_SIZE
                    && mouseY >= by && mouseY < by + BTN_SIZE;

            if (address != null) {
                context.drawTextWithShadow(mc.textRenderer, address, x + PAD, y + 3, 0xFFAAAAAA);
                context.drawTextWithShadow(mc.textRenderer, keyLabel, x + PAD, y + 14, 0xFFFFFFFF);
            } else {
                context.drawTextWithShadow(mc.textRenderer, keyLabel, x + PAD, y + (h - 8) / 2, 0xFFFFFFFF);
            }
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,
                    btnHov ? CROSS_BTN_HOV : CROSS_BTN,
                    bx, by, BTN_SIZE, BTN_SIZE);
        }

        @Override
        public boolean mouseClicked(Click click, boolean toggle) {
            if (click.button() != 0) return false;
            int bx = getX() + getWidth() - BTN_SIZE - PAD;
            int by = getY() + (getHeight() - BTN_SIZE) / 2;
            if (click.x() >= bx && click.x() < bx + BTN_SIZE && click.y() >= by && click.y() < by + BTN_SIZE) {
                onRemove.accept(data, click.hasShift());
            }
            return true;
        }

        @Override
        public Text getNarration() {
            return address != null
                ? Text.literal(address + "  •  " + keyLabel)
                : Text.literal(keyLabel);
        }
    }
}
