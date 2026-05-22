package com.thegameratort.mcgatekeeper.client.screen;

import com.thegameratort.mcgatekeeper.client.McgatekeeperClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Path;

public class KeyManagementScreen extends Screen {

    private static final int BOTTOM_H = 86;
    private static final int LIST_TOP = 40;
    private static final int ITEM_H = 25;

    private final Screen parent;

    private ServerKeyListWidget keyList;

    private Text statusMsg = Text.empty();
    private int statusColor = 0xFFAAAAAA;

    public KeyManagementScreen(Screen parent) {
        super(Text.translatable("screen.mcgatekeeper.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int listHeight = Math.max(ITEM_H, this.height - LIST_TOP - BOTTOM_H);
        keyList = new ServerKeyListWidget(this.client, 0, LIST_TOP, this.width, listHeight, ITEM_H);
        refreshList();
        addDrawableChild(keyList);

        int sTop = this.height - BOTTOM_H;
        int totalW = 100 + 4 + 100 + 4 + 100;
        int bX = (this.width - totalW) / 2;

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("screen.mcgatekeeper.export"),
                btn -> openExportDialog()
        ).dimensions(bX, sTop + 35, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("screen.mcgatekeeper.import"),
                btn -> openImportDialog()
        ).dimensions(bX + 104, sTop + 35, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("screen.mcgatekeeper.clear_all"),
                btn -> confirmClear()
        ).dimensions(bX + 208, sTop + 35, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.done"),
                btn -> close()
        ).dimensions(this.width / 2 - 100, sTop + 60, 200, 20).build());
    }

    private void refreshList() {
        keyList.populate(
                McgatekeeperClient.KEY_STORE.getEntries(),
                entry -> {
                    McgatekeeperClient.KEY_STORE.removeEntry(entry.serverKeyB64());
                    refreshList();
                }
        );
    }

    private String defaultFilePath() {
        return FabricLoader.getInstance().getGameDir().resolve("mcgatekeeper-keys.json").toString();
    }

    private void openExportDialog() {
        String def = defaultFilePath();
        assert client != null;
        Thread t = new Thread(() -> {
            String chosen = TinyFileDialogs.tinyfd_saveFileDialog(
                    "Export McGatekeeper Keys", def, null, null);
            if (chosen == null) return;
            Path path = Path.of(chosen);
            try {
                int count = McgatekeeperClient.KEY_STORE.exportTo(path);
                client.execute(() -> {
                    statusMsg = Text.translatable("screen.mcgatekeeper.status.exported",
                            count, path.getFileName().toString());
                    statusColor = 0xFF55FF55;
                });
            } catch (IOException e) {
                client.execute(() -> {
                    statusMsg = Text.translatable("screen.mcgatekeeper.status.error", e.getMessage());
                    statusColor = 0xFFFF5555;
                });
            }
        }, "mcgatekeeper-file-dialog");
        t.setDaemon(true);
        t.start();
    }

    private void openImportDialog() {
        String def = defaultFilePath();
        assert client != null;
        Thread t = new Thread(() -> {
            String chosen = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import McGatekeeper Keys", def, null, null, false);
            if (chosen == null) return;
            Path path = Path.of(chosen);
            try {
                int[] result = McgatekeeperClient.KEY_STORE.importFrom(path);
                client.execute(() -> {
                    statusMsg = Text.translatable("screen.mcgatekeeper.status.imported",
                            result[0], result[1]);
                    statusColor = result[0] > 0 ? 0xFF55FF55 : 0xFFAAAAAA;
                    refreshList();
                });
            } catch (IOException e) {
                client.execute(() -> {
                    statusMsg = Text.translatable("screen.mcgatekeeper.status.error", e.getMessage());
                    statusColor = 0xFFFF5555;
                });
            }
        }, "mcgatekeeper-file-dialog");
        t.setDaemon(true);
        t.start();
    }

    private void confirmClear() {
        assert client != null;
        client.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        McgatekeeperClient.KEY_STORE.getEntries()
                                .forEach(e -> McgatekeeperClient.KEY_STORE.removeEntry(e.serverKeyB64()));
                        statusMsg = Text.translatable("screen.mcgatekeeper.status.cleared");
                        statusColor = 0xFFAAAAAA;
                    }
                    client.setScreen(this);
                },
                Text.translatable("screen.mcgatekeeper.clear_confirm_title"),
                Text.translatable("screen.mcgatekeeper.clear_confirm_body")
        ));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("screen.mcgatekeeper.server_keys"), 20, 28, 0xFFAAAAAA);

        int sTop = this.height - BOTTOM_H;
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.mcgatekeeper.warning"), this.width / 2, sTop + 7, 0xFFFFAA00);

        if (!statusMsg.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, statusMsg,
                    this.width / 2, sTop + 21, statusColor);
        }
    }

    @Override
    public void close() {
        assert client != null;
        client.setScreen(parent);
    }
}
