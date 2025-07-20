package net.joe.sellingbin;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class Trade {
    private String name;
    private int sellPrice;
    private int sellAmount;
    private String color = Integer.toHexString(Formatting.DARK_GRAY.getColorIndex()).toUpperCase();
    private static final String DEFAULT_COLOR = "FF555555";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(int sellPrice) {
        this.sellPrice = sellPrice;
    }

    public int getSellAmount() {
        return sellAmount;
    }

    public void setSellAmount(int sellAmount) {
        this.sellAmount = sellAmount;
    }

    public int getColor() {
        if (this.color.equals("8")) {
            this.color = DEFAULT_COLOR;
        }
        return Integer.parseUnsignedInt(this.color, 16);
    }

    public void setColor(int color) {
        this.color = Integer.toHexString(color).toUpperCase();
    }

    public boolean matches(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).equals(Identifier.of(this.name));
    }
}