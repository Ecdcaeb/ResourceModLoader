package fr.nico.sqript.forge.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TreeSelector extends Button {

    public List<TreeSelector> choices = new ArrayList<TreeSelector>();
    public TreeSelector selected;
    public String label;
    public Item icon;
    int selectorId;
    private String displayText;
    private boolean extended = false;
    private final int extendedHeight;
    private CuboidInfos buttonDimensions;
    private boolean hover;
    private boolean hold;


    public TreeSelector(Style style, String label, Item icon) {
        super(style);
        this.extendedHeight = style.height;
        selectorId = new Random().nextInt(10000);
        this.label = label;
        this.icon = icon;
        // TODO Auto-generated constructor stub
    }

    public static boolean isMouseInBox(int mouseX, int mouseY, int x1, int y1, int x2, int y2) {
        return mouseX < x2 && mouseX > x1 && mouseY < y2 && mouseY > y1;
    }

    public boolean isExtended() {
        return extended;
    }

    public void setExtended(boolean extended) {
        this.extended = extended;
        if (extended) {
            getFrame().interactCode = selectorId;
            getFrame().isInteracting = true;
            this.style.height = (choices.size() + 1) * style.height;
        } else {
            getFrame().isInteracting = false;
            this.style.height = extendedHeight;
        }
    }

    public CuboidInfos getButtonDimensions() {
        return buttonDimensions;
    }

    public void setButtonDimensions(CuboidInfos buttonDimensions) {
        this.buttonDimensions = buttonDimensions;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        GlStateManager.pushMatrix();
        super.drawBorders(style.bordercolor);
        if (isMouseInBox(mouseX, mouseY, x, y, x + style.width, y + extendedHeight)) {
            drawRect(x, y, x + style.width, y + extendedHeight, style.hoverColor);
            checkClick(mouseX, mouseY);
        } else {
            drawRect(x, y, x + style.width, y + extendedHeight, style.backgroundcolor);

        }
        if (extended) {
            GlStateManager.translate(0, 0, 4);
            drawRect(this.x - style.bordersize, getY2() + style.bordersize, getX2() + style.bordersize, getY2(), style.bordercolor);
            GlStateManager.translate(0, 1, 0);

            int i = 1;
            for (TreeSelector s : choices) {

                CuboidInfos c = new CuboidInfos(this.x, this.y + extendedHeight * i, style.width, extendedHeight);

                if (c.isMouseOn(mouseX, mouseY)) {

                    drawRect(c.x, c.y, c.x + c.width, c.y + extendedHeight, this.style.getBackgroundcolor());

                    if (Mouse.isButtonDown(0)) {


                        this.selected = s;
                        if (!getFrame().hold) {
                            getFrame().handleAction(this, EnumAction.MOUSE_LEAVE);
                            getFrame().hold = true;
                        }
                    } else {

                        getFrame().hold = false;


                    }
                } else {


                }
                GlStateManager.pushMatrix();
                GlStateManager.translate(c.x + (c.height - Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT) / 2 + 15, 2 + c.y + extendedHeight / 2 - Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT / 2, 0);
                GlStateManager.scale(0.8, 0.8, 1);
                drawString(Minecraft.getMinecraft().fontRenderer, s.label.length() > 15 ? s.label.substring(0, 15) + "..." : s.label, 0, 0, 0xFFBBBBBB);

                GlStateManager.popMatrix();
                if (s.icon != null) {
                    this.drawItem(new ItemStack(s.icon, 1), c.x, c.y);
                }
                i++;
            }
            GlStateManager.translate(0, -1, 0);

        }
        GlStateManager.translate(this.x + (extendedHeight - Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT) / 2, this.y + extendedHeight / 2 - Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT / 2, 0);
        drawString(Minecraft.getMinecraft().fontRenderer, extended ? "▲" : "▼", this.style.width - 10, 0, 0xFFFFFFFF);

        GlStateManager.scale(0.8, 0.8, 1);
        drawString(Minecraft.getMinecraft().fontRenderer, this.label, 0, 0, 0xFFFFFFFF);


        GL11.glColor3d(1, 1, 1);
        GlStateManager.popMatrix();
    }

    private void drawTop(int mouseX, int mouseY) {

        drawString(Minecraft.getMinecraft().fontRenderer, displayText.replaceAll("&", "§"), x + 5, y + this.buttonDimensions.height / 2 - Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT / 2, 0xFFFFFFFF);
        drawString(Minecraft.getMinecraft().fontRenderer, extended ? "▲" : "▼", x + this.buttonDimensions.width - 10, y + this.buttonDimensions.height / 2 - Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT / 2, 0xFFFFFFFF);

    }

    @Override
    public void checkClick(int mouseX, int mouseY) {
        if (getFrame().hold == true) return;

        if (getFrame().isInteracting)
            if (getFrame().interactCode != selectorId) return;

        if (!hover && isMouseInBox(mouseX, mouseY, x, y, x + style.width, y + style.height) && Mouse.isButtonDown(0))
            return;
        hover = isMouseInBox(mouseX, mouseY, x, y, x + style.width, y + style.height);
        if (Mouse.isButtonDown(0) && isMouseInBox(mouseX, mouseY, x, y, x + style.width, y + style.height)) {
            if (!hold) {
                this.setExtended(!this.extended);
                hold = true;
            }
        } else {
            hold = false;
        }


    }

}


