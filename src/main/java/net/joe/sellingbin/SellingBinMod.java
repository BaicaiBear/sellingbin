package net.joe.sellingbin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.joe.sellingbin.bins.wooden.WoodenBinBlock;
import net.joe.sellingbin.bins.wooden.WoodenBinBlockEntity;
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
import net.minecraft.client.session.telemetry.WorldLoadTimesEvent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import static net.minecraft.server.command.CommandManager.literal;

public class SellingBinMod implements ModInitializer {

    public static final String defaultConfig = "{\n" +
            "\t\"minecraft:cobblestone\": {\n" +
            "\t  \"sellPrice\": 1,\n" +
            "\t  \"sellAmount\": 64,\n" +
            "\t  \"color\": \"FFAAAAAA\" // color stored in argb hex format (first two symbols is alpha) \n" +
            "\t},\n" +
            "\t\"minecraft:glowstone\": {\n" +
            "\t  \"sellPrice\": 3,\n" +
            "\t  \"sellAmount\": 16,\n" +
            "\t  \"color\": \"FFFFFF33\"\n" +
            "\t},\n" +
            "\t\"minecraft:wheat_seeds\": {\n" +
            "\t  \"sellPrice\": 1,\n" +
            "\t  \"sellAmount\": 64\n" +
            "\t}\n" +
            "}";

    public static final File configFile = new File("config/selling-bin.json");
    public static final Logger LOGGER = LoggerFactory.getLogger("selling-bin");
    public static final ArrayList<Trade> trades = new ArrayList<>();
    public static final Gson gson = new Gson();

    public static final Block WOODEN_BIN_BLOCK;
    public static final BlockItem WOODEN_BIN_BLOCK_ITEM;
    public static final BlockEntityType<WoodenBinBlockEntity> WOODEN_BIN_BLOCK_ENTITY;
    public static final Identifier WOODEN_BIN = Identifier.of("selling-bin", "wooden_bin");

    public static final PlayerInventoryManager inventoryManager = new PlayerInventoryManager();
    public static final File inventoryFile = new File("config/selling-bin.dat");
    private static HashMap<Identifier, Item> wrongIdItemsCheck;

    static {
        WOODEN_BIN_BLOCK = Registry.register(Registries.BLOCK, WOODEN_BIN, new WoodenBinBlock(FabricBlockSettings.copyOf(Blocks.CHEST).requiresTool()));
        WOODEN_BIN_BLOCK_ITEM = Registry.register(Registries.ITEM, WOODEN_BIN, new BlockItem(WOODEN_BIN_BLOCK, new Item.Settings()));
        WOODEN_BIN_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, WOODEN_BIN, FabricBlockEntityTypeBuilder.create(WoodenBinBlockEntity::new, WOODEN_BIN_BLOCK).build(null));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(content -> {
            content.add(WOODEN_BIN_BLOCK_ITEM);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("reloadbinconfig")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    reload();
                    context.getSource().sendMessage(Text.literal("Config reloaded."));
                    return 1;
                })));
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(ConfigSynchronizer.SyncPacket.ID, ConfigSynchronizer.SyncPacket.CODEC);
        
        if (!inventoryFile.exists()) {
            try {
                inventoryFile.createNewFile();
                inventoryManager.save(inventoryFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            inventoryManager.load(inventoryFile);
        }

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                inventoryManager.save(inventoryFile);
            }
        }, 0, 10 * 60 * 1000);

        Runtime.getRuntime().addShutdownHook(new ShutdownThread());

        ServerLifecycleEvents.SERVER_STARTING.register(s -> reload());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> dispatcher.register(
                literal("sellingbin_log_wrong_ids").executes(context -> {
                    wrongIdItemsCheck.forEach((key, value) -> {
                        if (value.equals(Items.AIR)) {
                            LOGGER.error("WRONG ITEM IDENTIFIER %s".formatted(key));
                            if (context.getSource().isExecutedByPlayer()) {
                                context.getSource().getPlayer().sendMessage(Text.literal("WRONG ITEM IDENTIFIER %s".formatted(key))
                                        .setStyle(Style.EMPTY.withColor(Formatting.RED)));
                            }
                        }
                    });
                    return 1;
                })
        ));

        ServerPlayConnectionEvents.INIT.register(ConfigSynchronizer::server);

        ServerTickEvents.END_WORLD_TICK.register((world) -> {
            if (world.getTimeOfDay() == 2000) {
                world.getServer().getPlayerManager().getPlayerList().forEach(WoodenBinBlockEntity::sellItems);
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient && world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof WoodenBinBlock) {

                NamedScreenHandlerFactory screenHandlerFactory = world.getBlockState(hitResult.getBlockPos()).createScreenHandlerFactory(world, hitResult.getBlockPos());
                if (screenHandlerFactory != null) {
                    player.openHandledScreen(screenHandlerFactory);
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    public static void reload() {
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(configFile))) {
                    bufferedWriter.write(defaultConfig);
                }
                LOGGER.info("Default config has been written to the file.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            wrongIdItemsCheck = new HashMap<>();
            JsonObject json = JsonParser.parseReader(new FileReader(configFile)).getAsJsonObject();
            for (String key : json.keySet()) {
                JsonElement tradeElement = json.get(key);
                Trade trade = gson.fromJson(tradeElement, Trade.class);
                trade.setName(key);

                Identifier id1 = Identifier.of(key);
                wrongIdItemsCheck.put(id1, Registries.ITEM.get(id1));
                trades.add(trade);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}