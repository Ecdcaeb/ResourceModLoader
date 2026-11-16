package rml.loader.content.presentation;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minecraft font, native controls, one scrollable form, and keyboard focus shared by every control. */
@SideOnly(Side.CLIENT)
final class PresentationDialogScreen extends GuiScreen {
    private final PresentationNetwork.OpenMessage dialog;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<Integer, GuiTextField> fields = new LinkedHashMap<>();
    private List<String> body;
    private int scroll, focus = -1, left, panelWidth, contentHeight;
    private boolean submitted;
    private String error = "";

    PresentationDialogScreen(PresentationNetwork.OpenMessage dialog) {
        this.dialog = dialog;
        for (PresentationDialogs.Input input : dialog.inputs) values.put(input.key, input.initial);
    }

    @Override public void initGui() {
        capture(); fields.clear(); buttonList.clear();
        panelWidth = Math.min(360, Math.max(100, width - 24)); left = (width - panelWidth) / 2;
        body = fontRenderer.listFormattedStringToWidth(dialog.body.getFormattedText(), panelWidth - 16);
        contentHeight = body.size() * 10 + 8 + dialog.inputs.length * 38 + dialog.actions.length * 24;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewportHeight())));
        for (int i = 0; i < dialog.inputs.length; i++) {
            PresentationDialogs.Input input = dialog.inputs[i]; int y = inputY(i);
            if (y < 40 || y + 34 > height - 42) continue;
            if (input.kind == PresentationDialogs.Kind.TEXT || input.kind == PresentationDialogs.Kind.NUMBER) {
                GuiTextField field = new GuiTextField(i, fontRenderer, left + 8, y + 12, panelWidth - 16, 20);
                field.setMaxStringLength(input.maxLength); field.setText(values.get(input.key)); field.setFocused(focus == i); fields.put(i, field);
            } else buttonList.add(new GuiButton(i, left + 8, y + 12, panelWidth - 16, 20, displayValue(input)));
        }
        for (int i = 0; i < dialog.actions.length; i++) {
            int y = actionY(i);
            if (y >= 40 && y + 20 <= height - 42) buttonList.add(new GuiButton(dialog.inputs.length + i, left + 8, y, panelWidth - 16, 20, dialog.actions[i].getFormattedText()));
        }
        buttonList.add(new GuiButton(-1, left + panelWidth - 88, height - 28, 80, 20, I18n.format("gui.cancel")));
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }

    private int viewportHeight() { return Math.max(38, height - 82); }
    private int inputY(int index) { return 40 + body.size() * 10 + 8 + index * 38 - scroll; }
    private int actionY(int index) { return inputY(dialog.inputs.length) + index * 24; }
    private String displayValue(PresentationDialogs.Input input) {
        String value = values.get(input.key);
        return input.kind == PresentationDialogs.Kind.BOOLEAN ? I18n.format(Boolean.parseBoolean(value) ? "options.on" : "options.off") : value;
    }
    private void capture() { for (Map.Entry<Integer, GuiTextField> field : fields.entrySet()) values.put(dialog.inputs[field.getKey()].key, field.getValue().getText()); }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, dialog.title.getFormattedText(), width / 2, 16, 0xFFFFFF);
        drawRect(left, 36, left + panelWidth, height - 36, 0xA0101010);
        for (int i = 0; i < body.size(); i++) {
            int y = 40 + i * 10 - scroll;
            if (y >= 40 && y + 10 <= height - 42) fontRenderer.drawString(body.get(i), left + 8, y, 0xE0E0E0);
        }
        for (int i = 0; i < dialog.inputs.length; i++) {
            int y = inputY(i);
            if (y >= 40 && y + 34 <= height - 42) fontRenderer.drawString(dialog.inputs[i].label.getFormattedText(), left + 8, y, 0xFFFFFF);
        }
        for (GuiTextField field : fields.values()) field.drawTextBox();
        for (GuiButton button : buttonList) if (button.id == focus) drawRect(button.x - 1, button.y - 1, button.x + button.width + 1, button.y + button.height + 1, 0xFFFFFFA0);
        if (contentHeight > viewportHeight()) {
            int track = viewportHeight(), thumb = Math.max(8, track * track / contentHeight);
            int y = 40 + scroll * (track - thumb) / Math.max(1, contentHeight - track);
            drawRect(left + panelWidth - 3, y, left + panelWidth - 1, y + thumb, 0xFFAAAAAA);
        }
        if (!error.isEmpty()) fontRenderer.drawString(fontRenderer.trimStringToWidth(error, Math.max(1, panelWidth - 100)), left + 8, height - 23, 0xFF8080);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override protected void actionPerformed(GuiButton button) {
        capture(); focus = button.id;
        if (button.id == -1) { mc.displayGuiScreen(null); return; }
        if (button.id < dialog.inputs.length) {
            PresentationDialogs.Input input = dialog.inputs[button.id];
            if (input.kind == PresentationDialogs.Kind.BOOLEAN) values.put(input.key, Boolean.toString(!Boolean.parseBoolean(values.get(input.key))));
            else if (input.kind == PresentationDialogs.Kind.CHOICE) {
                int index = java.util.Arrays.asList(input.choices).indexOf(values.get(input.key));
                values.put(input.key, input.choices[(index + 1) % input.choices.length]);
            }
            initGui(); return;
        }
        try { PresentationDialogs.validateInputs(dialog.inputs, values); }
        catch (IllegalArgumentException ex) { error = ex.getMessage(); return; }
        submitted = true;
        PresentationNetwork.choose(dialog.id, dialog.token, button.id - dialog.inputs.length, values);
        mc.displayGuiScreen(null);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        for (Map.Entry<Integer, GuiTextField> entry : fields.entrySet()) {
            entry.getValue().mouseClicked(mouseX, mouseY, mouseButton);
            if (entry.getValue().isFocused()) focus = entry.getKey();
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { mc.displayGuiScreen(null); return; }
        if (keyCode == 15) {
            int count = dialog.inputs.length + dialog.actions.length;
            focus = Math.floorMod(focus + (isShiftKeyDown() ? -1 : 1), count);
            int y = focus < dialog.inputs.length ? inputY(focus) : actionY(focus - dialog.inputs.length);
            if (y < 40) scroll -= 40 - y;
            else if (y + 34 > height - 42) scroll += y + 34 - height + 42;
            initGui(); return;
        }
        GuiTextField field = fields.get(focus);
        if (field != null && field.textboxKeyTyped(typedChar, keyCode)) return;
        if (keyCode == 28 || keyCode == 57) {
            for (GuiButton button : new ArrayList<>(buttonList)) if (button.id == focus) { actionPerformed(button); return; }
        }
        if (keyCode == 201 || keyCode == 209 || keyCode == 200 || keyCode == 208) {
            scroll += (keyCode == 201 || keyCode == 200 ? -1 : 1) * (keyCode == 201 || keyCode == 209 ? viewportHeight() : 20); initGui();
        }
    }
    @Override public void updateScreen() { for (GuiTextField field : fields.values()) field.updateCursorCounter(); }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) { scroll += wheel > 0 ? -24 : 24; initGui(); }
    }
    @Override public void onGuiClosed() {
        org.lwjgl.input.Keyboard.enableRepeatEvents(false);
        if (!submitted) PresentationNetwork.choose(dialog.id, dialog.token, -1, java.util.Collections.emptyMap());
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
