package top.bearcabbage.sellingbin;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Logger;

public class PlayerInventoryManager {
    private static final Logger LOGGER = Logger.getLogger(PlayerInventoryManager.class.getName());
    private final HashMap<UUID, PlayerInventory> playerInventories = new HashMap<>();

    public PlayerInventory getPlayerInventory(UUID playerId) {
        return playerInventories.computeIfAbsent(playerId, k -> new PlayerInventory());
    }

    public void setPlayerInventories(UUID playerID, PlayerInventory inventory) {
        playerInventories.put(playerID, inventory);
    }

    public void save(MinecraftServer server) {
        playerInventories.forEach((playerId, inventory) -> {
            NbtCompound sellingData = new NbtCompound();
            sellingData.put("sellingBinData", inventory.getWoodenBin().toNbtList(server.getRegistryManager()));
            SellingBinMod.SellingBinData.save(server.getPlayerManager().getPlayer(playerId), sellingData);
        });
    }
}