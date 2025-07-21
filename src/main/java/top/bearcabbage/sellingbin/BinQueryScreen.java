package top.bearcabbage.sellingbin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class BinQueryScreen implements NamedScreenHandlerFactory {
    ServerPlayerEntity player;

    private BinQueryScreen(ServerPlayerEntity player) {
        this.player = player;
    }

    public static BinQueryScreen of(ServerPlayerEntity player) {
        return new BinQueryScreen(player);
    }

    @Override
    public Text getDisplayName() {
        return Text.literal(player.getName().getLiteralString()+"的售货箱");
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory,
                SellingBinMod.inventoryManager.getPlayerInventory(this.player.getUuid()).getWoodenBin(), 3);

    }
}
