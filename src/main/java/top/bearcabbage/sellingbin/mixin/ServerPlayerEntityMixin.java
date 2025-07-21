package top.bearcabbage.sellingbin.mixin;

import com.mojang.authlib.GameProfile;
import eu.pb4.playerdata.api.PlayerDataApi;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.sellingbin.SellingBinInventory;
import top.bearcabbage.sellingbin.SellingBinPlayerAccessor;

import static top.bearcabbage.sellingbin.SellingBinMod.SellingBinData;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin extends PlayerEntity implements SellingBinPlayerAccessor {
    @Shadow @Final public MinecraftServer server;

    public ServerPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
        super(world, pos, yaw, gameProfile);
    }
    protected SellingBinInventory sellingBinInventory = SellingBinInventory.ofSize(27);

    @Inject(method = "<init>", at = @At("TAIL"))
    public void ServerPlayerEntity(MinecraftServer server, ServerWorld world, GameProfile profile, SyncedClientOptions clientOptions, CallbackInfo ci) {
        NbtCompound data = PlayerDataApi.getCustomDataFor(server, this.getUuid(), SellingBinData);
        if(data == null){
            data = new NbtCompound();
            data.put("SellingBinData", new NbtList());
            PlayerDataApi.setCustomDataFor(server, this.getUuid(), SellingBinData, data);
        }
        NbtList sellingDataList = (NbtList) data.get("SellingBinData");
        if (sellingDataList == null) sellingDataList = new NbtList();
        if (!sellingDataList.isEmpty()) {
            sellingBinInventory.readNbtList(sellingDataList, server.getRegistryManager());
        }
    }

    @Unique
    public void saveSellingData() {
        NbtCompound data = PlayerDataApi.getCustomDataFor(server, this.getUuid(), SellingBinData);
        if(data == null){
            data = new NbtCompound();
        }
        NbtList sellingDataList = sellingBinInventory.toNbtList(server.getRegistryManager());
        data.put("SellingBinData", sellingDataList);
        PlayerDataApi.setCustomDataFor(server, this.getUuid(), SellingBinData, data);
    }

    @Unique
    public SellingBinInventory getSellingBinInventory() {
        return sellingBinInventory;
    }
}
