package top.bearcabbage.sellingbin;

import com.glisco.numismaticoverhaul.item.MoneyBagItem;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class BinBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {

    public BinBlockEntity(BlockPos pos, BlockState state) {
        super(SellingBinMod.BIN_BLOCK_ENTITY, pos, state);
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory,
                SellingBinMod.inventoryManager.getPlayerInventory(player.getUuid()).getWoodenBin(), 3);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.selling-bin.wooden_bin");
    }

    public static void sellItems(PlayerEntity player) {
        var playerInventory = SellingBinMod.inventoryManager.getPlayerInventory(player.getUuid()).getWoodenBin();

        List<ItemStack> inventoryCopy = new ArrayList<>(playerInventory.getItems());
        List<Trade> trades = SellingBinMod.trades;

        playerInventory.clear();

        boolean hasSoldItems = false;
        int slotCounter = 0;
        for (ItemStack itemStack : inventoryCopy) {
            boolean isMatched = false;

            for (Trade trade : trades) {
                if (itemStack.getItem().getTranslationKey().equals(Registries.ITEM.get(Identifier.of(trade.getName())).getTranslationKey())) {
                    int sellAmount = trade.getSellAmount();
                    int currencyAmount = trade.getSellPrice();

                    if (itemStack.getCount() >= sellAmount) {
                        isMatched = true;
                        int remainingAmount = itemStack.getCount() % sellAmount;

                        if (remainingAmount != 0) {
                            handleItemRemainders(player, itemStack, slotCounter, remainingAmount);
                        }

                        handleSoldItems(player, currencyAmount, itemStack, sellAmount, slotCounter);
                        hasSoldItems = true;
                        break;
                    }
                }
            }

            if (!isMatched) {
                handleUnmatchedItems(player, itemStack, slotCounter);
            }
            slotCounter++;
        }

        if (hasSoldItems) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(Text.literal("勤劳的小熊商人帮你卖掉了箱子里的货物，把钱袋投入了箱子里～").formatted(Formatting.YELLOW).formatted(Formatting.BOLD));
            }
        }

    }

    private static void handleItemRemainders(PlayerEntity player, ItemStack itemStack, int slotCounter, int remainingAmount) {
        if (slotCounter > 26) {
            if(player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(Text.literal("收货箱里的"+itemStack.getCount()+"个"+itemStack.getItem().getName().getLiteralString()+"溢出来，拾荒者小熊悄悄捡走了～"));
            }
        } else {
            SellingBinMod.inventoryManager.getPlayerInventory(player.getUuid())
                    .getWoodenBin().setStack(slotCounter, new ItemStack(itemStack.getItem(), remainingAmount));
        }
    }

    private static void handleSoldItems(PlayerEntity player, int currencyAmount, ItemStack itemStack, int sellAmount, int slotCounter) {
        if (slotCounter > 26) {
            if(player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(Text.literal("收货箱里的钱币溢出来了，"+currencyAmount * (itemStack.getCount() / sellAmount)+"铜被可爱的小熊顺走了～"));
            }
        } else {
            SellingBinMod.inventoryManager.getPlayerInventory(player.getUuid()).getWoodenBin()
                    .setStack(slotCounter, MoneyBagItem.fromRawValue((long) currencyAmount * (itemStack.getCount() / sellAmount)));
        }
    }

    private static void handleUnmatchedItems(PlayerEntity player, ItemStack itemStack, int slotCounter) {
        if (slotCounter > 26) {
            if(player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(Text.literal("收货箱里的"+itemStack.getCount()+"个"+itemStack.getItem().getName()+"装不下了，被路边的小熊搬走了～"));
            }
        } else {
            SellingBinMod.inventoryManager.getPlayerInventory(player.getUuid()).getWoodenBin().setStack(slotCounter, itemStack);
        }
    }


}