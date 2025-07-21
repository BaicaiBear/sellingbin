package top.bearcabbage.sellingbin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.playerdata.api.PlayerDataApi;
import eu.pb4.playerdata.api.storage.NbtDataStorage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SellingBinMod implements ModInitializer {

    public static final String defaultConfig = "{\n" +
            "\t\"minecraft:cobblestone\": {\n" +
            "\t  \"sellPrice\": 1,\n" +
            "\t  \"sellAmount\": 64,\n" +
            "\t},\n" +
            "\t\"minecraft:glowstone\": {\n" +
            "\t  \"sellPrice\": 3,\n" +
            "\t  \"sellAmount\": 16,\n" +
            "\t},\n" +
            "\t\"minecraft:wheat_seeds\": {\n" +
            "\t  \"sellPrice\": 1,\n" +
            "\t  \"sellAmount\": 64\n" +
            "\t}\n" +
            "}";

    public static final File configFile = new File("config/selling-bin.json");
    public static final Logger LOGGER = LoggerFactory.getLogger("selling-bin");
    public static final NbtDataStorage SellingBinData = new NbtDataStorage("Selling_Bin_Data");
    public static final ArrayList<Trade> trades = new ArrayList<>();
    public static final Gson gson = new Gson();

    public static final Block BIN_BLOCK;
    public static final BlockItem BIN_BLOCK_ITEM;
    public static final BlockEntityType<BinBlockEntity> BIN_BLOCK_ENTITY;
    public static final Identifier WOODEN_BIN = Identifier.of("selling-bin", "wooden_bin");


    static {
        BIN_BLOCK = Registry.register(Registries.BLOCK, WOODEN_BIN, new BinBlock(FabricBlockSettings.copyOf(Blocks.CHEST).requiresTool()));
        BIN_BLOCK_ITEM = Registry.register(Registries.ITEM, WOODEN_BIN, new BlockItem(BIN_BLOCK, new Item.Settings()));
        BIN_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, WOODEN_BIN, FabricBlockEntityTypeBuilder.create(BinBlockEntity::new, BIN_BLOCK).build(null));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(content -> {
            content.add(BIN_BLOCK_ITEM);
        });
    }

    @Override
    public void onInitialize() {
        // Register selling bin data
        PlayerDataApi.register(SellingBinData);
        // Register SyncPacket for server
        PayloadTypeRegistry.playS2C().register(ConfigSynchronizer.SyncPacket.ID, ConfigSynchronizer.SyncPacket.CODEC);
        // Load config when the server starts
        ServerLifecycleEvents.SERVER_STARTING.register(s -> loadConfig());
        // Sync trades to client when connected
        ServerPlayConnectionEvents.INIT.register(ConfigSynchronizer::server);
        // Save selling data when disconnected
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ((SellingBinPlayerAccessor)(handler.player)).saveSellingData());
        ServerTickEvents.END_WORLD_TICK.register((world) -> {
            if (world.getTimeOfDay() % 2000 == 0) {
                // Sell all players' items at 8:00 daytime
                if (world.getTimeOfDay() == 2000) world.getServer().getPlayerManager().getPlayerList().forEach(BinBlockEntity::sellItems);
                // Periodically save data
                world.getServer().getPlayerManager().getPlayerList().forEach(player -> ((SellingBinPlayerAccessor)player).saveSellingData());
            };
        });
        // Command to see player's bin
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> dispatcher.register(
                literal("sellingbin-openas")
                        .then(argument("name", EntityArgumentType.entities())
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> {
                                    if (!context.getSource().isExecutedByPlayer()) {
                                        context.getSource().sendError(Text.literal("只能由玩家执行此指令"));
                                        return 0;
                                    }
                                    Collection<? extends Entity> targets = EntityArgumentType.getEntities(context,"name");
                                    if (targets.size()!=1) {
                                        context.getSource().sendError(Text.literal("只能选择「一个」玩家查看售货箱"));
                                        return 0;
                                    }
                                    Entity target = targets.iterator().next();
                                    if (target instanceof ServerPlayerEntity player) {
                                        NamedScreenHandlerFactory screenHandlerFactory = BinQueryScreen.of(player);
                                        context.getSource().getPlayer().openHandledScreen(screenHandlerFactory);
                                        return 1;
                                    } else {
                                        context.getSource().sendError(Text.literal("只能查看「玩家」的售货箱"));
                                        return 0;
                                    }
                                })
                        )));
        // Reload config command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("reloadbinconfig")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> {
                    loadConfig();
                    context.getSource().sendMessage(Text.literal("Config reloaded."));
                    return 1;
                })));
    }

    public static void loadConfig() {
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(configFile))) {
                    bufferedWriter.write(defaultConfig);
                }
                LOGGER.info("Default config has been written to the file.");
            } catch (IOException e) {
                LOGGER.warn(e.getMessage());
            }
        }

        try {
            JsonObject json = JsonParser.parseReader(new FileReader(configFile)).getAsJsonObject();
            for (String key : json.keySet()) {
                JsonElement tradeElement = json.get(key);
                Trade trade = gson.fromJson(tradeElement, Trade.class);
                trade.setName(key);

                Identifier id1 = Identifier.of(key);
                trades.add(trade);
            }
        } catch (FileNotFoundException e) {
            LOGGER.warn(e.getMessage());
        }
    }
}