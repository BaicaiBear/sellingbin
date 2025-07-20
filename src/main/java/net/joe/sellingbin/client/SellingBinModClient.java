package net.joe.sellingbin.client;

import net.joe.sellingbin.ConfigSynchronizer;
import net.joe.sellingbin.Trade;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class SellingBinModClient implements ClientModInitializer {
    public static final List<Trade> matches = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.INIT.register(ConfigSynchronizer::client);

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            for (Trade item : matches) {
                if (item.matches(stack)) {
                    MutableText tooltipText = Text.literal(String.format(I18n.translate("selling-bin.tooltip.sell"),
                            item.getSellAmount(),
                            item.getSellPrice()));

                    lines.add(tooltipText.setStyle(Style.EMPTY.withColor(item.getColor())));
                }
            }
        });
    }
}