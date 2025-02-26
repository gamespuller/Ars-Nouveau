package com.hollingsworth.arsnouveau.client.gui;

import com.hollingsworth.arsnouveau.ArsNouveau;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ProgressOption;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.SliderButton;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

public class BookSlider extends SliderButton {
    public BookSlider(Options pOptions, int pX, int pY, int pWidth, int pHeight, ProgressOption pProgressOption, List<FormattedCharSequence> pTooltip) {
        super(pOptions, pX, pY, pWidth, pHeight, pProgressOption, pTooltip);
    }

    @Override
    protected void renderBg(PoseStack pPoseStack, Minecraft pMinecraft, int pMouseX, int pMouseY) {
        RenderSystem.setShaderTexture(0, new ResourceLocation(ArsNouveau.MODID, "textures/gui/sound_bar_knob.png"));
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        this.blit(pPoseStack, this.x + (int)(this.value * (double)(this.width - 8)), this.y, 0,0, 8,20 , 8, 20);
    }

    public void setInitVal(double val){
        double d0 = this.value;
        this.value = Mth.clamp(val, 0.0D, 1.0D);
        if (d0 != this.value) {
            this.applyValue();
        }

        this.updateMessage();
    }

    @Override
    public void renderButton(PoseStack pPoseStack, int pMouseX, int pMouseY, float pPartialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, new ResourceLocation(ArsNouveau.MODID, "textures/gui/sound_bar.png"));
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);
        int i = this.getYImage(this.isHoveredOrFocused());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        this.blit(pPoseStack, this.x, this.y, 0,0, 100,20 , this.width, this.height);
        this.renderBg(pPoseStack, minecraft, pMouseX, pMouseY);
        int j = 10526880;

        font.draw(pPoseStack, this.getMessage(), this.x + this.width / 4, this.y + (this.height - 32) / 2, j | Mth.ceil(this.alpha * 255.0F) << 24);
    }

    @Override
    public void render(PoseStack pPoseStack, int pMouseX, int pMouseY, float pPartialTick) {
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick);
    }
}
