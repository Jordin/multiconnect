package net.earthcomputer.multiconnect.protocols.v1_12_2;

import com.google.common.base.Joiner;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Dynamic;
import io.netty.buffer.Unpooled;
import net.earthcomputer.multiconnect.api.Protocols;
import net.earthcomputer.multiconnect.api.ThreadSafe;
import net.earthcomputer.multiconnect.impl.ConnectionInfo;
import net.earthcomputer.multiconnect.protocols.ProtocolRegistry;
import net.earthcomputer.multiconnect.protocols.generic.ChunkData;
import net.earthcomputer.multiconnect.protocols.generic.ChunkDataTranslator;
import net.earthcomputer.multiconnect.protocols.generic.CustomPayloadHandler;
import net.earthcomputer.multiconnect.protocols.generic.DataTrackerManager;
import net.earthcomputer.multiconnect.protocols.generic.DefaultDynamicRegistries;
import net.earthcomputer.multiconnect.protocols.generic.IIdList;
import net.earthcomputer.multiconnect.protocols.generic.ISimpleRegistry;
import net.earthcomputer.multiconnect.protocols.generic.IUserDataHolder;
import net.earthcomputer.multiconnect.protocols.generic.PacketInfo;
import net.earthcomputer.multiconnect.protocols.generic.RegistryMutator;
import net.earthcomputer.multiconnect.protocols.generic.TagRegistry;
import net.earthcomputer.multiconnect.protocols.v1_11.Protocol_1_11;
import net.earthcomputer.multiconnect.protocols.v1_11_2.IScreenHandler;
import net.earthcomputer.multiconnect.protocols.v1_11_2.RecipeBookEmulator;
import net.earthcomputer.multiconnect.protocols.v1_12.PlaceRecipeC2SPacket_1_12;
import net.earthcomputer.multiconnect.protocols.v1_12_2.command.Commands_1_12_2;
import net.earthcomputer.multiconnect.protocols.v1_12_2.mixin.*;
import net.earthcomputer.multiconnect.protocols.v1_13.Protocol_1_13;
import net.earthcomputer.multiconnect.protocols.v1_13_2.Protocol_1_13_2;
import net.earthcomputer.multiconnect.protocols.v1_13_2.mixin.ZombieEntityAccessor;
import net.earthcomputer.multiconnect.protocols.v1_16_1.RecipeBookDataC2SPacket_1_16_1;
import net.earthcomputer.multiconnect.protocols.v1_16_5.MapUpdateS2CPacket_1_16_5;
import net.earthcomputer.multiconnect.protocols.v1_17_1.Biomes_1_17_1;
import net.earthcomputer.multiconnect.protocols.v1_17_1.Protocol_1_17_1;
import net.earthcomputer.multiconnect.transformer.InboundTranslator;
import net.earthcomputer.multiconnect.transformer.OutboundTranslator;
import net.earthcomputer.multiconnect.transformer.StringCustomPayload;
import net.earthcomputer.multiconnect.transformer.TransformerByteBuf;
import net.earthcomputer.multiconnect.transformer.UnsignedByte;
import net.earthcomputer.multiconnect.transformer.VarInt;
import net.minecraft.block.AbstractBannerBlock;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.InfestedBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.PistonHeadBlock;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallRedstoneTorchBlock;
import net.minecraft.block.WallSkullBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.command.CommandSource;
import net.minecraft.datafixer.fix.BlockStateFlattening;
import net.minecraft.datafixer.fix.EntityTheRenameningBlock;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.painting.PaintingMotive;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapIcon;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.play.PickFromInventoryC2SPacket;
import net.minecraft.network.packet.c2s.play.QueryBlockNbtC2SPacket;
import net.minecraft.network.packet.c2s.play.QueryEntityNbtC2SPacket;
import net.minecraft.network.packet.c2s.play.RenameItemC2SPacket;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateBeaconC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateCommandBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateCommandBlockMinecartC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateStructureBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket;
import net.minecraft.network.packet.s2c.play.CraftFailedResponseS2CPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.DifficultyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.LookAtS2CPacket;
import net.minecraft.network.packet.s2c.play.NbtQueryResponseS2CPacket;
import net.minecraft.network.packet.s2c.play.PaintingSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundIdS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.StatisticsS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import net.minecraft.network.packet.s2c.play.SynchronizeTagsS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.network.packet.s2c.play.UnlockRecipesS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.StatType;
import net.minecraft.stat.Stats;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.tag.ItemTags;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Protocol_1_12_2 extends Protocol_1_13 {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final Map<String, PaintingMotive> OLD_MOTIVE_NAMES = new HashMap<>();
    static {
        OLD_MOTIVE_NAMES.put("Kebab", PaintingMotive.KEBAB);
        OLD_MOTIVE_NAMES.put("Aztec", PaintingMotive.AZTEC);
        OLD_MOTIVE_NAMES.put("Alban", PaintingMotive.ALBAN);
        OLD_MOTIVE_NAMES.put("Aztec2", PaintingMotive.AZTEC2);
        OLD_MOTIVE_NAMES.put("Bomb", PaintingMotive.BOMB);
        OLD_MOTIVE_NAMES.put("Plant", PaintingMotive.PLANT);
        OLD_MOTIVE_NAMES.put("Wasteland", PaintingMotive.WASTELAND);
        OLD_MOTIVE_NAMES.put("Pool", PaintingMotive.POOL);
        OLD_MOTIVE_NAMES.put("Courbet", PaintingMotive.COURBET);
        OLD_MOTIVE_NAMES.put("Sea", PaintingMotive.SEA);
        OLD_MOTIVE_NAMES.put("Sunset", PaintingMotive.SUNSET);
        OLD_MOTIVE_NAMES.put("Creebet", PaintingMotive.CREEBET);
        OLD_MOTIVE_NAMES.put("Wanderer", PaintingMotive.WANDERER);
        OLD_MOTIVE_NAMES.put("Graham", PaintingMotive.GRAHAM);
        OLD_MOTIVE_NAMES.put("Match", PaintingMotive.MATCH);
        OLD_MOTIVE_NAMES.put("Bust", PaintingMotive.BUST);
        OLD_MOTIVE_NAMES.put("Stage", PaintingMotive.STAGE);
        OLD_MOTIVE_NAMES.put("Void", PaintingMotive.VOID);
        OLD_MOTIVE_NAMES.put("SkullAndRoses", PaintingMotive.SKULL_AND_ROSES);
        OLD_MOTIVE_NAMES.put("Wither", PaintingMotive.WITHER);
        OLD_MOTIVE_NAMES.put("Fighters", PaintingMotive.FIGHTERS);
        OLD_MOTIVE_NAMES.put("Pointer", PaintingMotive.POINTER);
        OLD_MOTIVE_NAMES.put("Pigscene", PaintingMotive.PIGSCENE);
        OLD_MOTIVE_NAMES.put("BurningSkull", PaintingMotive.BURNING_SKULL);
        OLD_MOTIVE_NAMES.put("Skeleton", PaintingMotive.SKELETON);
        OLD_MOTIVE_NAMES.put("DonkeyKong", PaintingMotive.DONKEY_KONG);
    }

    private static final Joiner DOT_JOINER = Joiner.on('.');

    private static final TrackedData<Integer> OLD_AREA_EFFECT_CLOUD_PARTICLE_ID = DataTrackerManager.createOldTrackedData(TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<Integer> OLD_AREA_EFFECT_CLOUD_PARTICLE_PARAM1 = DataTrackerManager.createOldTrackedData(TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<Integer> OLD_AREA_EFFECT_CLOUD_PARTICLE_PARAM2 = DataTrackerManager.createOldTrackedData(TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<String> OLD_CUSTOM_NAME = DataTrackerManager.createOldTrackedData(TrackedDataHandlerRegistry.STRING);
    public static final TrackedData<Integer> OLD_MINECART_DISPLAY_TILE = DataTrackerManager.createOldTrackedData(TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<Integer> OLD_WOLF_COLLAR_COLOR = DataTrackerManager.createOldTrackedData(TrackedDataHandlerRegistry.INTEGER);

    public static void registerTranslators() {
        ProtocolRegistry.registerInboundTranslator(ChunkData.class, buf -> {
            BitSet verticalStripBitmask = ChunkDataTranslator.current().getUserData(Protocol_1_17_1.VERTICAL_STRIP_BITMASK);
            buf.enablePassthroughMode();
            for (int sectionY = 0; sectionY < 16; sectionY++) {
                if (verticalStripBitmask.get(sectionY)) {
                    int paletteSize = ChunkData.skipPalette(buf, false);
                    if (paletteSize > 8) {
                        buf.disablePassthroughMode();
                        buf.readVarInt(); // dummy 0
                        buf.enablePassthroughMode();
                    }
                    buf.readLongArray(new long[paletteSize * 64]); // chunk data
                    buf.readBytesSingleAlloc(16 * 16 * 16 / 2); // block light
                    if (ChunkDataTranslator.current().getDimension().hasSkyLight())
                        buf.readBytesSingleAlloc(16 * 16 * 16 / 2); // sky light
                }
            }
            buf.disablePassthroughMode();
            if (ChunkDataTranslator.current().isFullChunk()) {
                for (int i = 0; i < 256; i++) {
                    buf.pendingRead(Integer.class, buf.readByte() & 0xff);
                }
            }
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(CustomPayloadS2CPacket.class, buf -> {
            String channel = buf.readString();
            Identifier newChannel;
            if ("MC|Brand".equals(channel)) {
                newChannel = CustomPayloadS2CPacket.BRAND;
            } else if ("MC|TrList".equals(channel)) {
                newChannel = Protocol_1_13_2.CUSTOM_PAYLOAD_TRADE_LIST;
            } else if ("MC|BOpen".equals(channel)) {
                newChannel = Protocol_1_13_2.CUSTOM_PAYLOAD_OPEN_BOOK;
            } else {
                newChannel = CustomPayloadHandler.getClientboundIdentifierForStringCustomPayload(channel);
            }
            buf.pendingRead(Identifier.class, newChannel);
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(PlaySoundIdS2CPacket.class, buf -> {
            Identifier id = Identifier.tryParse(buf.readString(256));
            if (id == null) {
                id = new Identifier("ambient.cave");
            }
            buf.pendingRead(Identifier.class, id);
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(MapUpdateS2CPacket_1_16_5.class, buf -> {
            buf.enablePassthroughMode();
            buf.readVarInt(); // map id
            buf.readByte(); // map scale
            buf.readBoolean(); // show icons
            int iconCount = buf.readVarInt();
            buf.disablePassthroughMode();
            for (int i = 0; i < iconCount; i++) {
                int metadata = buf.readByte();
                buf.pendingRead(MapIcon.Type.class, MapIcon.Type.byId((byte) ((metadata >> 4) & 15)));
                buf.enablePassthroughMode();
                buf.readByte(); // icon x
                buf.readByte(); // icon y
                buf.disablePassthroughMode();
                buf.pendingRead(Byte.class, (byte) (metadata & 15)); // rotation
                buf.pendingRead(Boolean.class, false); // has text
            }
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(ParticleS2CPacket.class, buf -> {
            buf.enablePassthroughMode();
            ParticleType<?> particleType = Registry.PARTICLE_TYPE.get(buf.readInt());
            if (particleType != ParticleTypes.ITEM
                    && particleType != ParticleTypes.DUST
                    && particleType != ParticleTypes.BLOCK
                    && particleType != ParticleTypes.FALLING_DUST
                    && particleType != Particles_1_12_2.BLOCK_DUST) {
                buf.disablePassthroughMode();
                buf.applyPendingReads();
                return;
            }
            buf.readBoolean(); // long distance
            buf.readFloat(); // x
            buf.readFloat(); // y
            buf.readFloat(); // z
            float red = 0, green = 0, blue = 0;
            if (particleType == ParticleTypes.DUST) {
                buf.disablePassthroughMode();
                red = buf.readFloat();
                green = buf.readFloat();
                blue = buf.readFloat();
                buf.pendingRead(Float.class, 0f); // offset x
                buf.pendingRead(Float.class, 0f); // offset y
                buf.pendingRead(Float.class, 0f); // offset z
                buf.enablePassthroughMode();
            } else {
                buf.readFloat(); // offset x
                buf.readFloat(); // offset y
                buf.readFloat(); // offset z
            }
            buf.readFloat(); // speed
            buf.readInt(); // count
            buf.disablePassthroughMode();
            if (particleType == ParticleTypes.ITEM) {
                Item item = Registry.ITEM.get(buf.readVarInt());
                int meta = buf.readVarInt();
                ItemStack stack = new ItemStack(item);
                stack.setDamage(meta);
                buf.pendingRead(ItemStack.class, stack);
            } else if (particleType == ParticleTypes.DUST) {
                buf.pendingRead(Float.class, red);
                buf.pendingRead(Float.class, green);
                buf.pendingRead(Float.class, blue);
                buf.pendingRead(Float.class, 1f); // scale
            } else {
                buf.pendingRead(VarInt.class, new VarInt(Blocks_1_12_2.convertToStateRegistryId(buf.readVarInt())));
            }
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(WorldEventS2CPacket.class, buf -> {
            buf.enablePassthroughMode();
            int eventId = buf.readInt();
            if (eventId != 2001) { // block broken
                buf.disablePassthroughMode();
                buf.applyPendingReads();
                return;
            }
            buf.readBlockPos(); // pos
            buf.disablePassthroughMode();
            buf.pendingRead(Integer.class, Blocks_1_12_2.convertToStateRegistryId(buf.readInt())); // block state id
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(CraftFailedResponseS2CPacket.class, buf -> {
            buf.enablePassthroughMode();
            buf.readByte(); // sync id
            buf.disablePassthroughMode();
            buf.pendingRead(Identifier.class, new Identifier(String.valueOf(buf.readVarInt())));
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(UnlockRecipesS2CPacket.class, buf -> {
            buf.enablePassthroughMode();
            var action = buf.readEnumConstant(UnlockRecipesS2CPacket.Action.class);
            buf.readBoolean(); // gui open
            buf.readBoolean(); // filtering craftable
            buf.pendingRead(Boolean.class, false); // furnace gui open
            buf.pendingRead(Boolean.class, false); // furnace filtering craftable
            int idChangeCount = buf.readVarInt();
            buf.disablePassthroughMode();
            for (int i = 0; i < idChangeCount; i++) {
                buf.pendingRead(Identifier.class, new Identifier(String.valueOf(buf.readVarInt())));
            }
            if (action == UnlockRecipesS2CPacket.Action.INIT) {
                buf.enablePassthroughMode();
                int idInitCount = buf.readVarInt();
                buf.disablePassthroughMode();
                for (int i = 0; i < idInitCount; i++) {
                    buf.pendingRead(Identifier.class, new Identifier(String.valueOf(buf.readVarInt())));
                }
            }
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(ScoreboardObjectiveUpdateS2CPacket.class, buf -> {
            buf.enablePassthroughMode();
            buf.readString(16); // name
            int mode = buf.readByte();
            buf.disablePassthroughMode();
            if (mode == 0 || mode == 2) {
                buf.pendingRead(Text.class, new LiteralText(buf.readString(32))); // display name
                String renderTypeName = buf.readString(16);
                var renderType = "hearts".equals(renderTypeName) ?
                        ScoreboardCriterion.RenderType.HEARTS : ScoreboardCriterion.RenderType.INTEGER;
                buf.pendingRead(ScoreboardCriterion.RenderType.class, renderType);
            }
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(PaintingSpawnS2CPacket.class, buf -> {
            buf.enablePassthroughMode();
            buf.readVarInt(); // id
            buf.readUuid(); // uuid
            buf.disablePassthroughMode();
            PaintingMotive motive = OLD_MOTIVE_NAMES.getOrDefault(buf.readString(13), PaintingMotive.KEBAB);
            buf.pendingRead(VarInt.class, new VarInt(Registry.PAINTING_MOTIVE.getRawId(motive)));
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(StatisticsS2CPacket.class, buf -> {
            int count = buf.readVarInt();
            var stats = new ArrayList<Triple<StatType<Object>, Object, Integer>>(count);
            for (int i = 0; i < count; i++) {
                String stat = buf.readString(32767);
                var statKey = translateStat(stat);
                int value = buf.readVarInt();
                if (statKey != null)
                    stats.add(Triple.of(statKey.getLeft(), statKey.getRight(), value));
            }
            buf.pendingRead(VarInt.class, new VarInt(stats.size()));
            for (var stat : stats) {
                buf.pendingRead(VarInt.class, new VarInt(Registry.STAT_TYPE.getRawId(stat.getLeft())));
                buf.pendingRead(VarInt.class, new VarInt(stat.getLeft().getRegistry().getRawId(stat.getMiddle())));
                buf.pendingRead(VarInt.class, new VarInt(stat.getRight()));
            }
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(CommandSuggestionsS2CPacket.class, buf -> {
            var entry = TabCompletionManager.nextEntry();
            if (entry == null) {
                LOGGER.error("Received unrequested tab completion packet");
                int count = buf.readVarInt();
                for (int i = 0; i < count; i++)
                    buf.readString(32767);
                buf.pendingRead(VarInt.class, new VarInt(0)); // completion id
                buf.pendingRead(VarInt.class, new VarInt(0)); // range start
                buf.pendingRead(VarInt.class, new VarInt(0)); // range length
                buf.pendingRead(VarInt.class, new VarInt(0)); // suggestion count
                buf.applyPendingReads();
                return;
            }

            buf.pendingRead(VarInt.class, new VarInt(entry.getId())); // completion id
            String message = entry.getMessage();
            int start = message.lastIndexOf(' ') + 1;
            if (start == 0 && message.startsWith("/"))
                start = 1;
            buf.pendingRead(VarInt.class, new VarInt(start)); // range start
            buf.pendingRead(VarInt.class, new VarInt(message.length() - start)); // range length
            buf.enablePassthroughMode();
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                buf.readString(32767); // suggestion
                buf.pendingRead(Boolean.class, false); // has tooltip
            }
            buf.disablePassthroughMode();
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(TeamS2CPacket.class, buf -> {
            buf.enablePassthroughMode();
            buf.readString(16); // team name
            int mode = buf.readByte();
            buf.disablePassthroughMode();
            if (mode == 0 || mode == 2) {
                buf.pendingRead(Text.class, new LiteralText(buf.readString(32))); // display name
                LiteralText prefix = new LiteralText(buf.readString(16));
                LiteralText suffix = new LiteralText(buf.readString(16));
                buf.enablePassthroughMode();
                buf.readByte(); // flags
                buf.readString(32); // name tag visibility rule
                buf.readString(32); // collision rule
                buf.disablePassthroughMode();
                Formatting color = Formatting.byColorIndex(buf.readByte());
                buf.pendingRead(Formatting.class, color);
                buf.pendingRead(Text.class, prefix);
                buf.pendingRead(Text.class, suffix);
            }
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(BossBarS2CPacket.class, buf -> {
            buf.enablePassthroughMode();
            buf.readUuid(); // uuid
            BossBarS2CPacket.Type type = buf.readEnumConstant(BossBarS2CPacket.Type.class);
            buf.disablePassthroughMode();
            if (type == BossBarS2CPacket.Type.UPDATE_PROPERTIES) {
                int flags = buf.readUnsignedByte();
                buf.pendingRead(UnsignedByte.class, new UnsignedByte((short) (flags | ((flags & 2) << 1)))); // copy bit 2 to 4
            }
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(EntitySpawnS2CPacket.class, buf -> {
            buf.enablePassthroughMode();
            buf.readVarInt(); // entity id
            buf.readUuid(); // uuid
            int type = buf.readByte();
            if (type != 70 && type != 71) { // falling block and item frame
                buf.disablePassthroughMode();
                buf.applyPendingReads();
                return;
            }
            buf.readDouble(); // x
            buf.readDouble(); // y
            buf.readDouble(); // z
            buf.readByte(); // pitch
            buf.readByte(); // yaw
            buf.disablePassthroughMode();
            if (type == 70) { // falling block
                buf.pendingRead(Integer.class, Blocks_1_12_2.convertToStateRegistryId(buf.readInt()));
            } else { // item frame
                buf.pendingRead(Integer.class, Direction.fromHorizontal(buf.readInt()).getId());
            }
            buf.applyPendingReads();
        });

        ProtocolRegistry.registerInboundTranslator(ItemStack.class, new InboundTranslator<>() {
            @Override
            public void onRead(TransformerByteBuf buf) {
                short itemId = buf.readShort();
                if (itemId == -1) {
                    buf.pendingRead(Short.class, itemId);
                    buf.applyPendingReads();
                    return;
                }
                byte count = buf.readByte();
                short meta = buf.readShort();
                NbtCompound tag = buf.readNbt();
                if (tag == null)
                    tag = new NbtCompound();
                tag.putShort("Damage", meta);
                buf.pendingRead(Short.class, itemId);
                buf.pendingRead(Byte.class, count);
                buf.pendingRead(NbtCompound.class, tag);
                buf.applyPendingReads();
            }

            @Override
            public ItemStack translate(ItemStack from) {
                if (from.isEmpty())
                    return from;
                from = from.copy();
                int meta = from.getDamage();
                assert from.getNbt() != null;
                from.getNbt().remove("Damage");
                if (from.getNbt().getSize() == 0)
                    from.setNbt(null);
                return Items_1_12_2.oldItemStackToNew(from, meta);
            }
        });

        ProtocolRegistry.registerOutboundTranslator(CraftRequestC2SPacket.class, buf -> {
            buf.passthroughWrite(Byte.class); // sync id
            Supplier<Identifier> recipeId = buf.skipWrite(Identifier.class);
            buf.pendingWrite(VarInt.class, () -> {
                try {
                    return new VarInt(Integer.parseInt(recipeId.get().getPath()));
                } catch (NumberFormatException e) {
                    return new VarInt(0);
                }
            }, val -> buf.writeVarInt(val.get()));
        });

        ProtocolRegistry.registerOutboundTranslator(RecipeBookDataC2SPacket_1_16_1.class, buf -> {
            var mode = buf.passthroughWrite(RecipeBookDataC2SPacket_1_16_1.Mode.class);
            buf.whenWrite(() -> {
                if (mode.get() == RecipeBookDataC2SPacket_1_16_1.Mode.SHOWN) {
                    Supplier<Identifier> recipeId = buf.skipWrite(Identifier.class);
                    buf.pendingWrite(Integer.class, () -> {
                        try {
                            return Integer.parseInt(recipeId.get().getPath());
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    }, buf::writeInt);
                } else if (mode.get() == RecipeBookDataC2SPacket_1_16_1.Mode.SETTINGS) {
                    buf.passthroughWrite(Boolean.class); // is gui open
                    buf.passthroughWrite(Boolean.class); // filtering craftable
                    buf.skipWrite(Boolean.class); // furnace gui open
                    buf.skipWrite(Boolean.class); // furnace filtering craftable
                }
            });
        });

        ProtocolRegistry.registerOutboundTranslator(RequestCommandCompletionsC2SPacket.class, buf -> {
            Supplier<VarInt> completionId = buf.skipWrite(VarInt.class);
            Supplier<String> command = buf.skipWrite(String.class);

            buf.whenWrite(() -> TabCompletionManager.addTabCompletionRequest(completionId.get().get(), command.get()));
            buf.pendingWrite(String.class, command, val -> buf.writeString(val, 32767));
            HitResult hitResult = MinecraftClient.getInstance().crosshairTarget;
            boolean hasTarget = hitResult != null && hitResult.getType() == HitResult.Type.BLOCK;
            buf.pendingWrite(Boolean.class, () -> false, buf::writeBoolean);
            buf.pendingWrite(Boolean.class, () -> hasTarget, buf::writeBoolean);
            if (hasTarget)
                buf.pendingWrite(BlockPos.class, ((BlockHitResult) hitResult)::getBlockPos, buf::writeBlockPos);
        });

        ProtocolRegistry.registerOutboundTranslator(ItemStack.class, new OutboundTranslator<>() {
            @Override
            public void onWrite(TransformerByteBuf buf) {
                Supplier<Short> itemId = buf.skipWrite(Short.class);
                buf.whenWrite(() -> {
                    if (itemId.get() == -1) {
                        buf.pendingWrite(Short.class, itemId, (Consumer<Short>) buf::writeShort);
                    } else {
                        Supplier<Byte> count = buf.skipWrite(Byte.class);
                        Supplier<NbtCompound> nbt = buf.skipWrite(NbtCompound.class);
                        buf.whenWrite(() -> {
                            NbtCompound oldNbt = nbt.get();
                            int meta = oldNbt.getInt("Damage");
                            oldNbt.remove("Damage");
                            buf.pendingWrite(Short.class, itemId, (Consumer<Short>) buf::writeShort);
                            buf.pendingWrite(Byte.class, count, (Consumer<Byte>) buf::writeByte);
                            buf.pendingWrite(Short.class, () -> (short) meta, (Consumer<Short>) buf::writeShort);
                            buf.pendingWrite(NbtCompound.class, () -> oldNbt.getSize() == 0 ? null : oldNbt, buf::writeNbt);
                        });
                    }
                });
            }

            @Override
            public ItemStack translate(ItemStack from) {
                Pair<ItemStack, Integer> itemAndMeta = Items_1_12_2.newItemStackToOld(from);
                ItemStack to = itemAndMeta.getLeft();
                to.setDamage(itemAndMeta.getRight());
                return to;
            }
        });

        BlockConnectors_1_12_2.register();
    }

    @SuppressWarnings("unchecked")
    private static <T> Pair<StatType<T>, T> translateStat(String statName) {
        String[] parts = statName.split("\\.");
        if (parts.length < 2 || !parts[0].equals("stat")) {
            return null;
        }

        if (parts.length == 2) {
            Identifier customStat = translateCustomStat(parts[1]);
            if (customStat == null) {
                return null;
            }
            return Pair.of((StatType<T>) Stats.CUSTOM, (T) customStat);
        }

        StatType<T> type;
        switch (parts[1]) {
            case "mineBlock":
                type = (StatType<T>) Stats.MINED;
                break;
            case "craftItem":
                type = (StatType<T>) Stats.CRAFTED;
                break;
            case "useItem":
                type = (StatType<T>) Stats.USED;
                break;
            case "breakItem":
                type = (StatType<T>) Stats.BROKEN;
                break;
            case "pickup":
                type = (StatType<T>) Stats.PICKED_UP;
                break;
            case "drop":
                type = (StatType<T>) Stats.DROPPED;
                break;
            case "killEntity":
                type = (StatType<T>) Stats.KILLED;
                break;
            case "entityKilledBy":
                type = (StatType<T>) Stats.KILLED_BY;
                break;
            default:
                return null;
        }

        if (type == Stats.KILLED || type == Stats.KILLED_BY) {
            String renamed = StatsCounterFixAccessor.getRenamedEntities().get(parts[2]);
            if (renamed == null) {
                return null;
            }
            T entityType = (T) Registry.ENTITY_TYPE.get(new Identifier(renamed));
            return Pair.of(type, entityType);
        }

        if (parts.length < 4) {
            return null;
        }
        Identifier id = Identifier.tryParse(parts[2] + ":" + DOT_JOINER.join(Arrays.asList(parts).subList(3, parts.length)));
        if (id == null || !type.getRegistry().containsId(id)) {
            return null;
        }
        return Pair.of(type, type.getRegistry().get(id));
    }

    private static Identifier translateCustomStat(String id) {
        return switch (id) {
            case "jump" -> Stats.JUMP;
            case "drop" -> Stats.DROP;
            case "deaths" -> Stats.DEATHS;
            case "mobKills" -> Stats.MOB_KILLS;
            case "pigOneCm" -> Stats.PIG_ONE_CM;
            case "flyOneCm" -> Stats.FLY_ONE_CM;
            case "leaveGame" -> Stats.LEAVE_GAME;
            case "diveOneCm" -> Stats.WALK_UNDER_WATER_ONE_CM;
            case "swimOneCm" -> Stats.SWIM_ONE_CM;
            case "fallOneCm" -> Stats.FALL_ONE_CM;
            case "walkOneCm" -> Stats.WALK_ONE_CM;
            case "boatOneCm" -> Stats.BOAT_ONE_CM;
            case "sneakTime" -> Stats.SNEAK_TIME;
            case "horseOneCm" -> Stats.HORSE_ONE_CM;
            case "sleepInBed" -> Stats.SLEEP_IN_BED;
            case "fishCaught" -> Stats.FISH_CAUGHT;
            case "climbOneCm" -> Stats.CLIMB_ONE_CM;
            case "aviateOneCm" -> Stats.AVIATE_ONE_CM;
            case "crouchOneCm" -> Stats.CROUCH_ONE_CM;
            case "sprintOneCm" -> Stats.SPRINT_ONE_CM;
            case "animalsBred" -> Stats.ANIMALS_BRED;
            case "chestOpened" -> Stats.OPEN_CHEST;
            case "damageTaken" -> Stats.DAMAGE_TAKEN;
            case "damageDealt" -> Stats.DAMAGE_DEALT;
            case "playerKills" -> Stats.PLAYER_KILLS;
            case "armorCleaned" -> Stats.CLEAN_ARMOR;
            case "flowerPotted" -> Stats.POT_FLOWER;
            case "recordPlayed" -> Stats.PLAY_RECORD;
            case "cauldronUsed" -> Stats.USE_CAULDRON;
            case "bannerCleaned" -> Stats.CLEAN_BANNER;
            case "itemEnchanted" -> Stats.ENCHANT_ITEM;
            case "playOneMinute" -> Stats.PLAY_TIME;
            case "minecartOneCm" -> Stats.MINECART_ONE_CM;
            case "timeSinceDeath" -> Stats.TIME_SINCE_DEATH;
            case "cauldronFilled" -> Stats.FILL_CAULDRON;
            case "noteblockTuned" -> Stats.TUNE_NOTEBLOCK;
            case "noteblockPlayed" -> Stats.PLAY_NOTEBLOCK;
            case "cakeSlicesEaten" -> Stats.EAT_CAKE_SLICE;
            case "hopperInspected" -> Stats.INSPECT_HOPPER;
            case "shulkerBoxOpened" -> Stats.OPEN_SHULKER_BOX;
            case "talkedToVillager" -> Stats.TALKED_TO_VILLAGER;
            case "enderchestOpened" -> Stats.OPEN_ENDERCHEST;
            case "dropperInspected" -> Stats.INSPECT_DROPPER;
            case "beaconInteraction" -> Stats.INTERACT_WITH_BEACON;
            case "furnaceInteraction" -> Stats.INTERACT_WITH_FURNACE;
            case "dispenserInspected" -> Stats.INSPECT_DISPENSER;
            case "tradedWithVillager" -> Stats.TRADED_WITH_VILLAGER;
            case "trappedChestTriggered" -> Stats.TRIGGER_TRAPPED_CHEST;
            case "brewingstandInteraction" -> Stats.INTERACT_WITH_BREWINGSTAND;
            case "craftingTableInteraction" -> Stats.INTERACT_WITH_CRAFTING_TABLE;
            case "junkFished" -> Protocol_1_11.JUNK_FISHED;
            case "treasureFished" -> Protocol_1_11.TREASURE_FISHED;
            default -> null;
        };
    }

    @Override
    public void setup(boolean resourceReload) {
        TabCompletionManager.reset();
        super.setup(resourceReload);
    }

    @Override
    public List<PacketInfo<?>> getClientboundPackets() {
        List<PacketInfo<?>> packets = super.getClientboundPackets();
        remove(packets, CommandSuggestionsS2CPacket.class);
        insertAfter(packets, DifficultyS2CPacket.class, PacketInfo.of(CommandSuggestionsS2CPacket.class, CommandSuggestionsS2CPacket::new));
        remove(packets, CommandTreeS2CPacket.class);
        remove(packets, NbtQueryResponseS2CPacket.class);
        remove(packets, LookAtS2CPacket.class);
        remove(packets, StopSoundS2CPacket.class);
        remove(packets, SynchronizeRecipesS2CPacket.class);
        remove(packets, SynchronizeTagsS2CPacket.class);
        return packets;
    }

    @Override
    public List<PacketInfo<?>> getServerboundPackets() {
        List<PacketInfo<?>> packets = super.getServerboundPackets();
        remove(packets, RequestCommandCompletionsC2SPacket.class);
        insertAfter(packets, TeleportConfirmC2SPacket.class, PacketInfo.of(RequestCommandCompletionsC2SPacket.class, RequestCommandCompletionsC2SPacket::new));
        remove(packets, QueryBlockNbtC2SPacket.class);
        remove(packets, BookUpdateC2SPacket.class);
        remove(packets, QueryEntityNbtC2SPacket.class);
        remove(packets, PickFromInventoryC2SPacket.class);
        remove(packets, RenameItemC2SPacket.class);
        remove(packets, SelectMerchantTradeC2SPacket.class);
        remove(packets, UpdateBeaconC2SPacket.class);
        remove(packets, UpdateCommandBlockC2SPacket.class);
        remove(packets, UpdateCommandBlockMinecartC2SPacket.class);
        remove(packets, UpdateStructureBlockC2SPacket.class);
        remove(packets, CustomPayloadC2SPacket.class);
        insertAfter(packets, CloseHandledScreenC2SPacket.class, PacketInfo.of(CustomPayloadC2SPacket_1_12_2.class, CustomPayloadC2SPacket_1_12_2::new));
        return packets;
    }

    @Override
    @ThreadSafe
    public boolean onSendPacket(Packet<?> packet) {
        if (!super.onSendPacket(packet))
            return false;
        if (packet.getClass() == QueryBlockNbtC2SPacket.class || packet.getClass() == QueryEntityNbtC2SPacket.class) {
            return false;
        }
        ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
        if (packet instanceof CustomPayloadC2SPacket customPayload) {
            checkConnectionValid(connection);
            String channel;
            if (customPayload.getChannel().equals(CustomPayloadC2SPacket.BRAND))
                channel = "MC|Brand";
            else
                channel = customPayload.getChannel().toString();
            connection.sendPacket(new CustomPayloadC2SPacket_1_12_2(channel, customPayload.getData()));
            return false;
        }
        if (packet instanceof BookUpdateC2SPacket bookUpdate) {
            checkConnectionValid(connection);
            TransformerByteBuf buf = new TransformerByteBuf(Unpooled.buffer(), null);
            String channel = bookUpdate.getTitle().isPresent() ? "MC|BSign" : "MC|BEdit";
            buf.writeTopLevelType(new StringCustomPayload(channel), IUserDataHolder.extractUserData(packet));
            ItemStack bookStack = createBookItemStack(bookUpdate.getTitle(), bookUpdate.getPages(), connection);
            buf.writeItemStack(bookStack);
            connection.sendPacket(new CustomPayloadC2SPacket_1_12_2(channel, buf));
            return false;
        }
        if (packet instanceof PickFromInventoryC2SPacket pickFromInventoryPacket) {
            checkConnectionValid(connection);
            TransformerByteBuf buf = new TransformerByteBuf(Unpooled.buffer(), null);
            buf.writeTopLevelType(new StringCustomPayload("MC|PickItem"), IUserDataHolder.extractUserData(packet));
            buf.writeVarInt(pickFromInventoryPacket.getSlot());
            connection.sendPacket(new CustomPayloadC2SPacket_1_12_2("MC|PickItem", buf));
            return false;
        }
        if (packet instanceof RenameItemC2SPacket renameItem) {
            checkConnectionValid(connection);
            TransformerByteBuf buf = new TransformerByteBuf(Unpooled.buffer(), null);
            buf.writeTopLevelType(new StringCustomPayload("MC|ItemName"), IUserDataHolder.extractUserData(packet));
            buf.writeString(renameItem.getName(), 32767);
            connection.sendPacket(new CustomPayloadC2SPacket_1_12_2("MC|ItemName", buf));
            return false;
        }
        if (packet instanceof SelectMerchantTradeC2SPacket selectTrade) {
            checkConnectionValid(connection);
            TransformerByteBuf buf = new TransformerByteBuf(Unpooled.buffer(), null);
            buf.writeTopLevelType(new StringCustomPayload("MC|TrSel"), IUserDataHolder.extractUserData(packet));
            buf.writeInt(selectTrade.getTradeId());
            connection.sendPacket(new CustomPayloadC2SPacket_1_12_2("MC|TrSel", buf));
            return false;
        }
        if (packet instanceof UpdateBeaconC2SPacket updateBeacon) {
            checkConnectionValid(connection);
            TransformerByteBuf buf = new TransformerByteBuf(Unpooled.buffer(), null);
            buf.writeTopLevelType(new StringCustomPayload("MC|Beacon"), IUserDataHolder.extractUserData(packet));
            buf.writeInt(updateBeacon.getPrimaryEffectId());
            buf.writeInt(updateBeacon.getSecondaryEffectId());
            connection.sendPacket(new CustomPayloadC2SPacket_1_12_2("MC|Beacon", buf));
            return false;
        }
        if (packet instanceof UpdateCommandBlockC2SPacket updateCmdBlock) {
            checkConnectionValid(connection);
            TransformerByteBuf buf = new TransformerByteBuf(Unpooled.buffer(), null);
            buf.writeTopLevelType(new StringCustomPayload("MC|AutoCmd"), IUserDataHolder.extractUserData(packet));
            buf.writeInt(updateCmdBlock.getBlockPos().getX());
            buf.writeInt(updateCmdBlock.getBlockPos().getY());
            buf.writeInt(updateCmdBlock.getBlockPos().getZ());
            buf.writeString(updateCmdBlock.getCommand());
            buf.writeBoolean(updateCmdBlock.shouldTrackOutput());
            switch (updateCmdBlock.getType()) {
                case AUTO -> buf.writeString("AUTO");
                case REDSTONE -> buf.writeString("REDSTONE");
                case SEQUENCE -> buf.writeString("SEQUENCE");
                default -> {
                    LOGGER.error("Unknown command block type: " + updateCmdBlock.getType());
                    return false;
                }
            }
            buf.writeBoolean(updateCmdBlock.isConditional());
            buf.writeBoolean(updateCmdBlock.isAlwaysActive());
            connection.sendPacket(new CustomPayloadC2SPacket_1_12_2("MC|AutoCmd", buf));
            return false;
        }
        if (packet instanceof UpdateCommandBlockMinecartC2SPacket updateCmdMinecart) {
            checkConnectionValid(connection);
            TransformerByteBuf buf = new TransformerByteBuf(Unpooled.buffer(), null);
            buf.writeTopLevelType(new StringCustomPayload("MC|AdvCmd"), IUserDataHolder.extractUserData(packet));
            buf.writeByte(1); // command block type (minecart)
            buf.writeInt(((CommandBlockMinecartC2SAccessor) updateCmdMinecart).getEntityId());
            buf.writeString(updateCmdMinecart.getCommand());
            buf.writeBoolean(updateCmdMinecart.shouldTrackOutput());
            connection.sendPacket(new CustomPayloadC2SPacket_1_12_2("MC|AdvCmd", buf));
            return false;
        }
        if (packet instanceof UpdateStructureBlockC2SPacket updateStructBlock) {
            checkConnectionValid(connection);
            TransformerByteBuf buf = new TransformerByteBuf(Unpooled.buffer(), null);
            buf.writeTopLevelType(new StringCustomPayload("MC|Struct"), IUserDataHolder.extractUserData(packet));
            buf.writeInt(updateStructBlock.getPos().getX());
            buf.writeInt(updateStructBlock.getPos().getY());
            buf.writeInt(updateStructBlock.getPos().getZ());
            switch (updateStructBlock.getAction()) {
                case UPDATE_DATA -> buf.writeByte(1);
                case SAVE_AREA -> buf.writeByte(2);
                case LOAD_AREA -> buf.writeByte(3);
                case SCAN_AREA -> buf.writeByte(4);
                default -> {
                    LOGGER.error("Unknown structure block action: " + updateStructBlock.getAction());
                    return false;
                }
            }
            switch (updateStructBlock.getMode()) {
                case SAVE -> buf.writeString("SAVE");
                case LOAD -> buf.writeString("LOAD");
                case CORNER -> buf.writeString("CORNER");
                case DATA -> buf.writeString("DATA");
                default -> {
                    LOGGER.error("Unknown structure block mode: " + updateStructBlock.getMode());
                    return false;
                }
            }
            buf.writeString(updateStructBlock.getStructureName());
            buf.writeInt(updateStructBlock.getOffset().getX());
            buf.writeInt(updateStructBlock.getOffset().getY());
            buf.writeInt(updateStructBlock.getOffset().getZ());
            buf.writeInt(updateStructBlock.getSize().getX());
            buf.writeInt(updateStructBlock.getSize().getY());
            buf.writeInt(updateStructBlock.getSize().getZ());
            switch (updateStructBlock.getMirror()) {
                case NONE -> buf.writeString("NONE");
                case LEFT_RIGHT -> buf.writeString("LEFT_RIGHT");
                case FRONT_BACK -> buf.writeString("FRONT_BACK");
                default -> {
                    LOGGER.error("Unknown mirror: " + updateStructBlock.getMirror());
                    return false;
                }
            }
            switch (updateStructBlock.getRotation()) {
                case NONE -> buf.writeString("NONE");
                case CLOCKWISE_90 -> buf.writeString("CLOCKWISE_90");
                case CLOCKWISE_180 -> buf.writeString("CLOCKWISE_180");
                case COUNTERCLOCKWISE_90 -> buf.writeString("COUNTERCLOCKWISE_90");
                default -> {
                    LOGGER.error("Unknown rotation: " + updateStructBlock.getRotation());
                    return false;
                }
            }
            buf.writeString(updateStructBlock.getMetadata());
            buf.writeBoolean(updateStructBlock.shouldIgnoreEntities());
            buf.writeBoolean(updateStructBlock.shouldShowAir());
            buf.writeBoolean(updateStructBlock.shouldShowBoundingBox());
            buf.writeFloat(updateStructBlock.getIntegrity());
            buf.writeVarLong(updateStructBlock.getSeed());
            // have fun with all that, server!
            connection.sendPacket(new CustomPayloadC2SPacket_1_12_2("MC|Struct", buf));
            return false;
        }
        if (packet instanceof PlaceRecipeC2SPacket_1_12 recipePlacement) {
            // emulate furnace recipe placements
            if (ConnectionInfo.protocolVersion != Protocols.V1_12) {
                MinecraftClient.getInstance().execute(() -> {
                    PlayerEntity player = MinecraftClient.getInstance().player;
                    if (player != null) {
                        RecipeBookEmulator recipeBookEmulator = ((IScreenHandler) player.currentScreenHandler).multiconnect_getRecipeBookEmulator();
                        recipeBookEmulator.emulateRecipePlacement(recipePlacement);
                    }
                });
                return false;
            } else {
                if (MinecraftClient.getInstance().isOnThread()) {
                    PlayerEntity player = MinecraftClient.getInstance().player;
                    if (player != null
                            && player.currentScreenHandler instanceof AbstractRecipeScreenHandler<?> recipeScreenHandler
                            && recipeScreenHandler.getCategory() != RecipeBookCategory.CRAFTING) {
                        RecipeBookEmulator recipeBookEmulator = ((IScreenHandler) player.currentScreenHandler).multiconnect_getRecipeBookEmulator();
                        recipeBookEmulator.emulateRecipePlacement(recipePlacement);
                        return false;
                    }
                } else {
                    checkConnectionValid(connection);
                    MinecraftClient.getInstance().execute(() -> {
                        PlayerEntity player = MinecraftClient.getInstance().player;
                        if (player != null
                                && player.currentScreenHandler instanceof AbstractRecipeScreenHandler<?> recipeScreenHandler
                                && recipeScreenHandler.getCategory() != RecipeBookCategory.CRAFTING) {
                            RecipeBookEmulator recipeBookEmulator = ((IScreenHandler) player.currentScreenHandler).multiconnect_getRecipeBookEmulator();
                            recipeBookEmulator.emulateRecipePlacement(recipePlacement);
                        } else {
                            connection.sendPacket(packet);
                        }
                    });
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    protected void removeTrackedDataHandlers() {
        super.removeTrackedDataHandlers();
        removeTrackedDataHandler(TrackedDataHandlerRegistry.OPTIONAL_TEXT_COMPONENT);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T readTrackedData(TrackedDataHandler<T> handler, PacketByteBuf buf) {
        if (handler == TrackedDataHandlerRegistry.OPTIONAL_BLOCK_STATE) {
            int stateId = buf.readVarInt();
            if (stateId == 0)
                return (T) Optional.empty();
            return (T) Optional.ofNullable(Block.STATE_IDS.get(Blocks_1_12_2.convertToStateRegistryId(stateId)));
        }
        return super.readTrackedData(handler, buf);
    }

    @Override
    public boolean acceptEntityData(Class<? extends Entity> clazz, TrackedData<?> data) {
        if (!super.acceptEntityData(clazz, data))
            return false;

        if (clazz == AreaEffectCloudEntity.class && data == AreaEffectCloudEntityAccessor.getParticleId()) {
            DataTrackerManager.registerOldTrackedData(AreaEffectCloudEntity.class,
                    OLD_AREA_EFFECT_CLOUD_PARTICLE_ID,
                    Registry.PARTICLE_TYPE.getRawId(ParticleTypes.ENTITY_EFFECT),
                    (entity, val) -> {
                ParticleType<?> type = Registry.PARTICLE_TYPE.get(val);
                if (type == null)
                    type = ParticleTypes.ENTITY_EFFECT;
                setParticleType(entity, type);
            });
            DataTrackerManager.registerOldTrackedData(AreaEffectCloudEntity.class,
                    OLD_AREA_EFFECT_CLOUD_PARTICLE_PARAM1,
                    0,
                    (entity, val) -> {
                ((IAreaEffectCloudEntity) entity).multiconnect_setParam1(val);
                setParticleType(entity, entity.getParticleType().getType());
            });
            DataTrackerManager.registerOldTrackedData(AreaEffectCloudEntity.class,
                    OLD_AREA_EFFECT_CLOUD_PARTICLE_PARAM2,
                    0,
                    (entity, val) -> {
                ((IAreaEffectCloudEntity) entity).multiconnect_setParam2(val);
                setParticleType(entity, entity.getParticleType().getType());
            });
            return false;
        }

        if (clazz == Entity.class && data == EntityAccessor.getCustomName()) {
            DataTrackerManager.registerOldTrackedData(Entity.class, OLD_CUSTOM_NAME, "",
                    (entity, val) -> entity.setCustomName(val.isEmpty() ? null : new LiteralText(val)));
            return false;
        }

        if (clazz == BoatEntity.class && data == BoatEntityAccessor.getBubbleWobbleTicks()) {
            return false;
        }

        if (clazz == ZombieEntity.class && data == ZombieEntityAccessor.getConvertingInWater()) {
            return false;
        }

        if (clazz == AbstractMinecartEntity.class) {
            TrackedData<Integer> displayTile = AbstractMinecartEntityAccessor.getCustomBlockId();
            if (data == displayTile) {
                DataTrackerManager.registerOldTrackedData(AbstractMinecartEntity.class, OLD_MINECART_DISPLAY_TILE, 0,
                        (entity, val) -> entity.getDataTracker().set(displayTile, Blocks_1_12_2.convertToStateRegistryId(val)));
                return false;
            }
        }

        if (clazz == WolfEntity.class) {
            TrackedData<Integer> collarColor = WolfEntityAccessor.getCollarColor();
            if (data == collarColor) {
                DataTrackerManager.registerOldTrackedData(WolfEntity.class, OLD_WOLF_COLLAR_COLOR, 1,
                        (entity, val) -> entity.getDataTracker().set(collarColor, 15 - val));
                return false;
            }
        }

        return true;
    }

    private static void setParticleType(AreaEffectCloudEntity entity, ParticleType<?> type) {
        IAreaEffectCloudEntity iaece = (IAreaEffectCloudEntity) entity;
        if (type.getParametersFactory() == ItemStackParticleEffect.PARAMETERS_FACTORY) {
            Item item = Registry.ITEM.get(iaece.multiconnect_getParam1());
            int meta = iaece.multiconnect_getParam2();
            ItemStack stack = Items_1_12_2.oldItemStackToNew(new ItemStack(item), meta);
            entity.setParticleType(createParticle(type, buf -> buf.writeItemStack(stack)));
        } else if (type.getParametersFactory() == BlockStateParticleEffect.PARAMETERS_FACTORY) {
            entity.setParticleType(createParticle(type, buf -> buf.writeVarInt(iaece.multiconnect_getParam1())));
        } else if (type.getParametersFactory() == DustParticleEffect.PARAMETERS_FACTORY) {
            entity.setParticleType(createParticle(type, buf -> {
                buf.writeFloat(1);
                buf.writeFloat(0);
                buf.writeFloat(0);
                buf.writeFloat(1);
            }));
        } else {
            entity.setParticleType(createParticle(type, buf -> {}));
        }
    }

    private static <T extends ParticleEffect> T createParticle(ParticleType<T> type, Consumer<PacketByteBuf> function) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        function.accept(buf);
        return type.getParametersFactory().read(type, buf);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void recomputeBlockStates() {
        final int leavesId = Registry.BLOCK.getRawId(Blocks.OAK_LEAVES);
        final int leaves2Id = Registry.BLOCK.getRawId(Blocks.ACACIA_LEAVES);
        final int torchId = Registry.BLOCK.getRawId(Blocks.TORCH);
        final int redstoneTorchId = Registry.BLOCK.getRawId(Blocks.REDSTONE_TORCH);
        final int unlitRedstoneTorchId = Registry.BLOCK.getRawId(Blocks_1_12_2.UNLIT_REDSTONE_TORCH);
        final int skullId = Registry.BLOCK.getRawId(Blocks.SKELETON_SKULL);
        final int tallGrassId = Registry.BLOCK.getRawId(Blocks.GRASS);
        final int chestId = Registry.BLOCK.getRawId(Blocks.CHEST);
        final int enderChestId = Registry.BLOCK.getRawId(Blocks.ENDER_CHEST);
        final int trappedChestId = Registry.BLOCK.getRawId(Blocks.TRAPPED_CHEST);
        final int wallBannerId = Registry.BLOCK.getRawId(Blocks.WHITE_WALL_BANNER);

        ((IIdList<BlockState>) Block.STATE_IDS).multiconnect_clear();
        for (int blockId = 0; blockId < 256; blockId++) {
            if (blockId == leavesId) {
                registerLeavesStates(blockId, Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES, Blocks.JUNGLE_LEAVES);
            } else if (blockId == leaves2Id) {
                registerLeavesStates(blockId, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.ACACIA_LEAVES, Blocks.ACACIA_LEAVES);
            } else if (blockId == torchId) {
                registerTorchStates(blockId, Blocks.TORCH.getDefaultState(), Blocks.WALL_TORCH.getDefaultState());
            } else if (blockId == redstoneTorchId) {
                registerTorchStates(blockId, Blocks.REDSTONE_TORCH.getDefaultState(), Blocks.REDSTONE_WALL_TORCH.getDefaultState());
            } else if (blockId == unlitRedstoneTorchId) {
                registerTorchStates(blockId, Blocks.REDSTONE_TORCH.getDefaultState().with(RedstoneTorchBlock.LIT, false), Blocks.REDSTONE_WALL_TORCH.getDefaultState().with(WallRedstoneTorchBlock.LIT, false));
            } else if (blockId == skullId) {
                final Direction[] dirs = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP};
                for (int meta = 0; meta < 16; meta++) {
                    Direction dir = dirs[meta & 7];
                    BlockState state;
                    if (dir == Direction.DOWN || dir == Direction.UP) {
                        state = Blocks.SKELETON_SKULL.getDefaultState();
                    } else {
                        state = Blocks.SKELETON_WALL_SKULL.getDefaultState().with(WallSkullBlock.FACING, dir);
                    }
                    Block.STATE_IDS.set(state, blockId << 4 | meta);
                }
            } else if (blockId == tallGrassId) {
                Block.STATE_IDS.set(Blocks.DEAD_BUSH.getDefaultState(), blockId << 4);
                Block.STATE_IDS.set(Blocks.GRASS.getDefaultState(), blockId << 4 | 1);
                Block.STATE_IDS.set(Blocks.FERN.getDefaultState(), blockId << 4 | 2);
                for (int meta = 3; meta < 16; meta++)
                    Block.STATE_IDS.set(Blocks.DEAD_BUSH.getDefaultState(), blockId << 4 | meta);
            } else if (blockId == chestId) {
                registerHorizontalFacingStates(blockId, Blocks.CHEST);
            } else if (blockId == enderChestId) {
                registerHorizontalFacingStates(blockId, Blocks.ENDER_CHEST);
            } else if (blockId == trappedChestId) {
                registerHorizontalFacingStates(blockId, Blocks.TRAPPED_CHEST);
            } else if (blockId == wallBannerId) {
                registerHorizontalFacingStates(blockId, Blocks.WHITE_WALL_BANNER);
            } else {
                for (int meta = 0; meta < 16; meta++) {
                    Dynamic<?> dynamicState = BlockStateFlattening.lookupState(blockId << 4 | meta);
                    String fixedName = dynamicState.get("Name").asString("");
                    if (meta == 0 || fixedName.equals(BlockStateFlattening.lookupStateBlock(blockId << 4)))
                        fixedName = BlockStateReverseFlattening.reverseLookupStateBlock(blockId << 4);
                    fixedName = EntityTheRenameningBlock.BLOCKS.getOrDefault(fixedName, fixedName);
                    Block block = Registry.BLOCK.get(new Identifier(fixedName));
                    if (block == Blocks.AIR && blockId != 0) {
                        dynamicState = BlockStateReverseFlattening.reverseLookupState(blockId << 4 | meta);
                        fixedName = dynamicState.get("Name").asString("");
                        block = Registry.BLOCK.get(new Identifier(fixedName));
                    }
                    if (block != Blocks.AIR || blockId == 0) {
                        var stateManager = block instanceof DummyBlock ? ((DummyBlock) block).original.getBlock().getStateManager() : block.getStateManager();
                        BlockState _default = block instanceof DummyBlock ? ((DummyBlock) block).original : block.getDefaultState();
                        BlockState state = _default;
                        for (Map.Entry<String, String> entry : dynamicState.get("Properties").asMap(k -> k.asString(""), v -> v.asString("")).entrySet()) {
                            state = addProperty(stateManager, state, entry.getKey(), entry.getValue());
                        }
                        if (!acceptBlockState(state))
                            state = _default;
                        Block.STATE_IDS.set(state, blockId << 4 | meta);
                    }
                }
            }
        }
        Set<BlockState> addedStates = new HashSet<>();
        Block.STATE_IDS.iterator().forEachRemaining(addedStates::add);

        for (Block block : Registry.BLOCK) {
            for (BlockState state : block.getStateManager().getStates()) {
                if (!addedStates.contains(state) && acceptBlockState(state)) {
                    Block.STATE_IDS.add(state);
                }
            }
        }
    }

    private void registerLeavesStates(int blockId, Block... leavesBlocks) {
        for (int type = 0; type < 4; type++) {
            Block.STATE_IDS.set(leavesBlocks[type].getDefaultState(), blockId << 4 | type);
            Block.STATE_IDS.set(leavesBlocks[type].getDefaultState().with(LeavesBlock.PERSISTENT, true), blockId << 4 | 4 | type);
            Block.STATE_IDS.set(leavesBlocks[type].getDefaultState().with(LeavesBlock.DISTANCE, 6), blockId << 4 | 8 | type);
            Block.STATE_IDS.set(leavesBlocks[type].getDefaultState().with(LeavesBlock.PERSISTENT, true).with(LeavesBlock.DISTANCE, 6), blockId << 4 | 12 | type);
        }
    }

    private void registerTorchStates(int blockId, BlockState torch, BlockState wallTorch) {
        Block.STATE_IDS.set(torch, blockId << 4);
        Block.STATE_IDS.set(wallTorch.with(WallTorchBlock.FACING, Direction.EAST), blockId << 4 | 1);
        Block.STATE_IDS.set(wallTorch.with(WallTorchBlock.FACING, Direction.WEST), blockId << 4 | 2);
        Block.STATE_IDS.set(wallTorch.with(WallTorchBlock.FACING, Direction.SOUTH), blockId << 4 | 3);
        Block.STATE_IDS.set(wallTorch.with(WallTorchBlock.FACING, Direction.NORTH), blockId << 4 | 4);
        for (int meta = 5; meta < 16; meta++)
            Block.STATE_IDS.set(torch, blockId << 4 | meta);
    }

    private void registerHorizontalFacingStates(int blockId, Block block) {
        registerHorizontalFacingStates(blockId, block, block);
    }

    private void registerHorizontalFacingStates(int blockId, Block standingBlock, Block wallBlock) {
        Block.STATE_IDS.set(standingBlock.getDefaultState(), blockId << 4);
        Block.STATE_IDS.set(standingBlock.getDefaultState(), blockId << 4 | 1);
        Block.STATE_IDS.set(wallBlock.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH), blockId << 4 | 2);
        Block.STATE_IDS.set(wallBlock.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH), blockId << 4 | 3);
        Block.STATE_IDS.set(wallBlock.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST), blockId << 4 | 4);
        Block.STATE_IDS.set(wallBlock.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST), blockId << 4 | 5);
        for (int meta = 6; meta < 16; meta++)
            Block.STATE_IDS.set(standingBlock.getDefaultState(), blockId << 4 | meta);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState addProperty(StateManager<Block, BlockState> stateManager, BlockState state, String propName, String valName) {
        Property<T> prop = (Property<T>) stateManager.getProperty(propName);
        return prop == null ? state : state.with(prop, prop.parse(valName).orElseGet(() -> state.get(prop)));
    }

    @Override
    public boolean acceptBlockState(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.TNT)
            return super.acceptBlockState(state.with(TntBlock.UNSTABLE, false)); // re-add unstable because it was absent from 1.13.0 :thonkjang:

        if (!super.acceptBlockState(state))
            return false;

        if (block instanceof LeavesBlock && state.get(LeavesBlock.DISTANCE) < 6)
            return false;
        if (block == Blocks.PISTON_HEAD && state.get(PistonHeadBlock.SHORT))
            return false;
        if (state.getEntries().containsKey(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED))
            return false;
        if (block == Blocks.LEVER) {
            WallMountLocation face = state.get(LeverBlock.FACE);
            Direction facing = state.get(LeverBlock.FACING);
            if ((face == WallMountLocation.FLOOR || face == WallMountLocation.CEILING) && (facing == Direction.SOUTH || facing == Direction.EAST))
                return false;
        }
        if (block instanceof TrapdoorBlock && state.get(TrapdoorBlock.POWERED))
            return false;
        if ((block == Blocks.OAK_WOOD || block == Blocks.SPRUCE_WOOD
                || block == Blocks.BIRCH_WOOD || block == Blocks.JUNGLE_WOOD
                || block == Blocks.ACACIA_WOOD || block == Blocks.DARK_OAK_WOOD)
                && state.get(PillarBlock.AXIS) != Direction.Axis.Y)
            return false;
        return true;
    }

    @Override
    public float getBlockHardness(BlockState state, float hardness) {
        if (state.getBlock() instanceof InfestedBlock) {
            return 0.75f;
        }
        return super.getBlockHardness(state, hardness);
    }

    @Override
    public BlockState getActualState(World world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof FlowerPotBlock) {
            if (world.getBlockEntity(pos) instanceof FlowerPotBlockEntity flowerPot) {
                BlockState flowerPotState = flowerPot.getFlowerPotState();
                if (flowerPotState != null) {
                    return flowerPotState;
                }
            }
        } else if (state.getBlock() instanceof AbstractSkullBlock) {
            if (world.getBlockEntity(pos) instanceof ISkullBlockEntity skull) {
                return skull.multiconnect_getActualState();
            }
        }
        return super.getActualState(world, pos, state);
    }

    @Override
    public void addExtraBlockTags(TagRegistry<Block> tags) {
        tags.add(BlockTags.WOOL,
            Blocks.WHITE_WOOL,
            Blocks.ORANGE_WOOL,
            Blocks.MAGENTA_WOOL,
            Blocks.LIGHT_BLUE_WOOL,
            Blocks.YELLOW_WOOL,
            Blocks.LIME_WOOL,
            Blocks.PINK_WOOL,
            Blocks.GRAY_WOOL,
            Blocks.LIGHT_GRAY_WOOL,
            Blocks.CYAN_WOOL,
            Blocks.PURPLE_WOOL,
            Blocks.BLUE_WOOL,
            Blocks.BROWN_WOOL,
            Blocks.GREEN_WOOL,
            Blocks.RED_WOOL,
            Blocks.BLACK_WOOL);
        tags.add(BlockTags.PLANKS,
            Blocks.OAK_PLANKS,
            Blocks.SPRUCE_PLANKS,
            Blocks.BIRCH_PLANKS,
            Blocks.JUNGLE_PLANKS,
            Blocks.ACACIA_PLANKS,
            Blocks.DARK_OAK_PLANKS);
        tags.add(BlockTags.STONE_BRICKS,
            Blocks.STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICKS,
            Blocks.CRACKED_STONE_BRICKS,
            Blocks.CHISELED_STONE_BRICKS);
        tags.add(BlockTags.WOODEN_BUTTONS, Blocks.OAK_BUTTON);
        tags.addTag(BlockTags.BUTTONS, BlockTags.WOODEN_BUTTONS);
        tags.add(BlockTags.BUTTONS, Blocks.STONE_BUTTON);
        tags.add(BlockTags.CARPETS,
            Blocks.WHITE_CARPET,
            Blocks.ORANGE_CARPET,
            Blocks.MAGENTA_CARPET,
            Blocks.LIGHT_BLUE_CARPET,
            Blocks.YELLOW_CARPET,
            Blocks.LIME_CARPET,
            Blocks.PINK_CARPET,
            Blocks.GRAY_CARPET,
            Blocks.LIGHT_GRAY_CARPET,
            Blocks.CYAN_CARPET,
            Blocks.PURPLE_CARPET,
            Blocks.BLUE_CARPET,
            Blocks.BROWN_CARPET,
            Blocks.GREEN_CARPET,
            Blocks.RED_CARPET,
            Blocks.BLACK_CARPET);
        tags.add(BlockTags.WOODEN_DOORS,
            Blocks.OAK_DOOR,
            Blocks.SPRUCE_DOOR,
            Blocks.BIRCH_DOOR,
            Blocks.JUNGLE_DOOR,
            Blocks.ACACIA_DOOR,
            Blocks.DARK_OAK_DOOR);
        tags.add(BlockTags.WOODEN_STAIRS,
            Blocks.OAK_STAIRS,
            Blocks.SPRUCE_STAIRS,
            Blocks.BIRCH_STAIRS,
            Blocks.JUNGLE_STAIRS,
            Blocks.ACACIA_STAIRS,
            Blocks.DARK_OAK_STAIRS);
        tags.add(BlockTags.WOODEN_SLABS,
            Blocks.OAK_SLAB,
            Blocks.SPRUCE_SLAB,
            Blocks.BIRCH_SLAB,
            Blocks.JUNGLE_SLAB,
            Blocks.ACACIA_SLAB,
            Blocks.DARK_OAK_SLAB);
        tags.addTag(BlockTags.DOORS, BlockTags.WOODEN_DOORS);
        tags.add(BlockTags.DOORS, Blocks.IRON_DOOR);
        tags.add(BlockTags.SAPLINGS,
            Blocks.OAK_SAPLING,
            Blocks.SPRUCE_SAPLING,
            Blocks.BIRCH_SAPLING,
            Blocks.JUNGLE_SAPLING,
            Blocks.ACACIA_SAPLING,
            Blocks.DARK_OAK_SAPLING);
        tags.add(BlockTags.DARK_OAK_LOGS,
            Blocks.DARK_OAK_LOG,
            Blocks.DARK_OAK_WOOD);
        tags.add(BlockTags.OAK_LOGS,
            Blocks.OAK_LOG,
            Blocks.OAK_WOOD);
        tags.add(BlockTags.ACACIA_LOGS,
            Blocks.ACACIA_LOG,
            Blocks.ACACIA_WOOD);
        tags.add(BlockTags.BIRCH_LOGS,
            Blocks.BIRCH_LOG,
            Blocks.BIRCH_WOOD);
        tags.add(BlockTags.JUNGLE_LOGS,
            Blocks.JUNGLE_LOG,
            Blocks.JUNGLE_WOOD);
        tags.add(BlockTags.SPRUCE_LOGS,
            Blocks.SPRUCE_LOG,
            Blocks.SPRUCE_WOOD);
        tags.addTag(BlockTags.LOGS, BlockTags.DARK_OAK_LOGS);
        tags.addTag(BlockTags.LOGS, BlockTags.OAK_LOGS);
        tags.addTag(BlockTags.LOGS, BlockTags.ACACIA_LOGS);
        tags.addTag(BlockTags.LOGS, BlockTags.BIRCH_LOGS);
        tags.addTag(BlockTags.LOGS, BlockTags.JUNGLE_LOGS);
        tags.addTag(BlockTags.LOGS, BlockTags.SPRUCE_LOGS);
        tags.add(BlockTags.ANVIL,
            Blocks.ANVIL,
            Blocks.CHIPPED_ANVIL,
            Blocks.DAMAGED_ANVIL);
        tags.add(BlockTags.ENDERMAN_HOLDABLE,
            Blocks.GRASS_BLOCK,
            Blocks.DIRT,
            Blocks.COARSE_DIRT,
            Blocks.PODZOL,
            Blocks.SAND,
            Blocks.RED_SAND,
            Blocks.GRAVEL,
            Blocks.BROWN_MUSHROOM,
            Blocks.RED_MUSHROOM,
            Blocks.TNT,
            Blocks.CACTUS,
            Blocks.CLAY,
            Blocks.CARVED_PUMPKIN,
            Blocks.MELON,
            Blocks.MYCELIUM,
            Blocks.NETHERRACK);
        tags.add(BlockTags.FLOWER_POTS,
            Blocks.FLOWER_POT,
            Blocks.POTTED_POPPY,
            Blocks.POTTED_BLUE_ORCHID,
            Blocks.POTTED_ALLIUM,
            Blocks.POTTED_AZURE_BLUET,
            Blocks.POTTED_RED_TULIP,
            Blocks.POTTED_ORANGE_TULIP,
            Blocks.POTTED_WHITE_TULIP,
            Blocks.POTTED_PINK_TULIP,
            Blocks.POTTED_OXEYE_DAISY,
            Blocks.POTTED_DANDELION,
            Blocks.POTTED_OAK_SAPLING,
            Blocks.POTTED_SPRUCE_SAPLING,
            Blocks.POTTED_BIRCH_SAPLING,
            Blocks.POTTED_JUNGLE_SAPLING,
            Blocks.POTTED_ACACIA_SAPLING,
            Blocks.POTTED_DARK_OAK_SAPLING,
            Blocks.POTTED_RED_MUSHROOM,
            Blocks.POTTED_BROWN_MUSHROOM,
            Blocks.POTTED_DEAD_BUSH,
            Blocks.POTTED_FERN,
            Blocks.POTTED_CACTUS);
        tags.add(BlockTags.BANNERS,
            Blocks.WHITE_BANNER,
            Blocks.ORANGE_BANNER,
            Blocks.MAGENTA_BANNER,
            Blocks.LIGHT_BLUE_BANNER,
            Blocks.YELLOW_BANNER,
            Blocks.LIME_BANNER,
            Blocks.PINK_BANNER,
            Blocks.GRAY_BANNER,
            Blocks.LIGHT_GRAY_BANNER,
            Blocks.CYAN_BANNER,
            Blocks.PURPLE_BANNER,
            Blocks.BLUE_BANNER,
            Blocks.BROWN_BANNER,
            Blocks.GREEN_BANNER,
            Blocks.RED_BANNER,
            Blocks.BLACK_BANNER,
            Blocks.WHITE_WALL_BANNER,
            Blocks.ORANGE_WALL_BANNER,
            Blocks.MAGENTA_WALL_BANNER,
            Blocks.LIGHT_BLUE_WALL_BANNER,
            Blocks.YELLOW_WALL_BANNER,
            Blocks.LIME_WALL_BANNER,
            Blocks.PINK_WALL_BANNER,
            Blocks.GRAY_WALL_BANNER,
            Blocks.LIGHT_GRAY_WALL_BANNER,
            Blocks.CYAN_WALL_BANNER,
            Blocks.PURPLE_WALL_BANNER,
            Blocks.BLUE_WALL_BANNER,
            Blocks.BROWN_WALL_BANNER,
            Blocks.GREEN_WALL_BANNER,
            Blocks.RED_WALL_BANNER,
            Blocks.BLACK_WALL_BANNER);
        tags.add(BlockTags.WOODEN_PRESSURE_PLATES, Blocks.OAK_PRESSURE_PLATE);
        tags.add(BlockTags.STAIRS,
            Blocks.OAK_STAIRS,
            Blocks.COBBLESTONE_STAIRS,
            Blocks.SPRUCE_STAIRS,
            Blocks.SANDSTONE_STAIRS,
            Blocks.ACACIA_STAIRS,
            Blocks.JUNGLE_STAIRS,
            Blocks.BIRCH_STAIRS,
            Blocks.DARK_OAK_STAIRS,
            Blocks.NETHER_BRICK_STAIRS,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.BRICK_STAIRS,
            Blocks.PURPUR_STAIRS,
            Blocks.QUARTZ_STAIRS,
            Blocks.RED_SANDSTONE_STAIRS);
        tags.add(BlockTags.SLABS,
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.STONE_BRICK_SLAB,
            Blocks.SANDSTONE_SLAB,
            Blocks.ACACIA_SLAB,
            Blocks.BIRCH_SLAB,
            Blocks.DARK_OAK_SLAB,
            Blocks.JUNGLE_SLAB,
            Blocks.OAK_SLAB,
            Blocks.SPRUCE_SLAB,
            Blocks.PURPUR_SLAB,
            Blocks.QUARTZ_SLAB,
            Blocks.RED_SANDSTONE_SLAB,
            Blocks.BRICK_SLAB,
            Blocks.COBBLESTONE_SLAB,
            Blocks.NETHER_BRICK_SLAB,
            Blocks.PETRIFIED_OAK_SLAB);
        tags.add(BlockTags.SAND,
            Blocks.SAND,
            Blocks.RED_SAND);
        tags.add(BlockTags.RAILS,
            Blocks.RAIL,
            Blocks.POWERED_RAIL,
            Blocks.DETECTOR_RAIL,
            Blocks.ACTIVATOR_RAIL);
        tags.add(BlockTags.ICE,
            Blocks.ICE,
            Blocks.PACKED_ICE,
            Blocks.BLUE_ICE,
            Blocks.FROSTED_ICE);
        tags.add(BlockTags.VALID_SPAWN,
            Blocks.GRASS_BLOCK,
            Blocks.PODZOL);
        tags.add(BlockTags.LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.OAK_LEAVES,
            Blocks.SPRUCE_LEAVES,
            Blocks.DARK_OAK_LEAVES,
            Blocks.ACACIA_LEAVES,
            Blocks.BIRCH_LEAVES);
        tags.add(BlockTags.IMPERMEABLE,
            Blocks.GLASS,
            Blocks.WHITE_STAINED_GLASS,
            Blocks.ORANGE_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.LIGHT_BLUE_STAINED_GLASS,
            Blocks.YELLOW_STAINED_GLASS,
            Blocks.LIME_STAINED_GLASS,
            Blocks.PINK_STAINED_GLASS,
            Blocks.GRAY_STAINED_GLASS,
            Blocks.LIGHT_GRAY_STAINED_GLASS,
            Blocks.CYAN_STAINED_GLASS,
            Blocks.PURPLE_STAINED_GLASS,
            Blocks.BLUE_STAINED_GLASS,
            Blocks.BROWN_STAINED_GLASS,
            Blocks.GREEN_STAINED_GLASS,
            Blocks.RED_STAINED_GLASS,
            Blocks.BLACK_STAINED_GLASS);
        tags.add(BlockTags.WOODEN_TRAPDOORS, Blocks.OAK_TRAPDOOR);
        tags.addTag(BlockTags.TRAPDOORS, BlockTags.WOODEN_TRAPDOORS);
        tags.add(BlockTags.TRAPDOORS, Blocks.IRON_TRAPDOOR);
        tags.add(BlockTags.CORAL_BLOCKS);
        tags.add(BlockTags.CORALS);
        tags.add(BlockTags.WALL_CORALS);
        super.addExtraBlockTags(tags);
    }

    @Override
    public void addExtraItemTags(TagRegistry<Item> tags, TagRegistry<Block> blockTags) {
        copyBlocks(tags, blockTags, ItemTags.WOOL, BlockTags.WOOL);
        copyBlocks(tags, blockTags, ItemTags.PLANKS, BlockTags.PLANKS);
        copyBlocks(tags, blockTags, ItemTags.STONE_BRICKS, BlockTags.STONE_BRICKS);
        copyBlocks(tags, blockTags, ItemTags.WOODEN_BUTTONS, BlockTags.WOODEN_BUTTONS);
        copyBlocks(tags, blockTags, ItemTags.BUTTONS, BlockTags.BUTTONS);
        copyBlocks(tags, blockTags, ItemTags.CARPETS, BlockTags.CARPETS);
        copyBlocks(tags, blockTags, ItemTags.WOODEN_DOORS, BlockTags.WOODEN_DOORS);
        copyBlocks(tags, blockTags, ItemTags.WOODEN_STAIRS, BlockTags.WOODEN_STAIRS);
        copyBlocks(tags, blockTags, ItemTags.WOODEN_SLABS, BlockTags.WOODEN_SLABS);
        copyBlocks(tags, blockTags, ItemTags.WOODEN_PRESSURE_PLATES, BlockTags.WOODEN_PRESSURE_PLATES);
        copyBlocks(tags, blockTags, ItemTags.DOORS, BlockTags.DOORS);
        copyBlocks(tags, blockTags, ItemTags.SAPLINGS, BlockTags.SAPLINGS);
        copyBlocks(tags, blockTags, ItemTags.OAK_LOGS, BlockTags.OAK_LOGS);
        copyBlocks(tags, blockTags, ItemTags.DARK_OAK_LOGS, BlockTags.DARK_OAK_LOGS);
        copyBlocks(tags, blockTags, ItemTags.BIRCH_LOGS, BlockTags.BIRCH_LOGS);
        copyBlocks(tags, blockTags, ItemTags.ACACIA_LOGS, BlockTags.ACACIA_LOGS);
        copyBlocks(tags, blockTags, ItemTags.SPRUCE_LOGS, BlockTags.SPRUCE_LOGS);
        copyBlocks(tags, blockTags, ItemTags.JUNGLE_LOGS, BlockTags.JUNGLE_LOGS);
        copyBlocks(tags, blockTags, ItemTags.LOGS, BlockTags.LOGS);
        copyBlocks(tags, blockTags, ItemTags.SAND, BlockTags.SAND);
        copyBlocks(tags, blockTags, ItemTags.SLABS, BlockTags.SLABS);
        copyBlocks(tags, blockTags, ItemTags.STAIRS, BlockTags.STAIRS);
        copyBlocks(tags, blockTags, ItemTags.ANVIL, BlockTags.ANVIL);
        copyBlocks(tags, blockTags, ItemTags.RAILS, BlockTags.RAILS);
        copyBlocks(tags, blockTags, ItemTags.LEAVES, BlockTags.LEAVES);
        copyBlocks(tags, blockTags, ItemTags.WOODEN_TRAPDOORS, BlockTags.WOODEN_TRAPDOORS);
        copyBlocks(tags, blockTags, ItemTags.TRAPDOORS, BlockTags.TRAPDOORS);
        tags.add(ItemTags.BANNERS,
            Items.WHITE_BANNER,
            Items.ORANGE_BANNER,
            Items.MAGENTA_BANNER,
            Items.LIGHT_BLUE_BANNER,
            Items.YELLOW_BANNER,
            Items.LIME_BANNER,
            Items.PINK_BANNER,
            Items.GRAY_BANNER,
            Items.LIGHT_GRAY_BANNER,
            Items.CYAN_BANNER,
            Items.PURPLE_BANNER,
            Items.BLUE_BANNER,
            Items.BROWN_BANNER,
            Items.GREEN_BANNER,
            Items.RED_BANNER,
            Items.BLACK_BANNER);
        tags.add(ItemTags.BOATS,
            Items.OAK_BOAT,
            Items.SPRUCE_BOAT,
            Items.BIRCH_BOAT,
            Items.JUNGLE_BOAT,
            Items.ACACIA_BOAT,
            Items.DARK_OAK_BOAT);
        tags.add(ItemTags.FISHES,
            Items.COD,
            Items.COOKED_COD,
            Items.SALMON,
            Items.COOKED_SALMON,
            Items.PUFFERFISH,
            Items.TROPICAL_FISH);
        super.addExtraItemTags(tags, blockTags);
    }

    @Override
    public void addExtraFluidTags(TagRegistry<Fluid> tags) {
        tags.add(FluidTags.WATER, Fluids.WATER, Fluids.FLOWING_WATER);
        tags.add(FluidTags.LAVA, Fluids.LAVA, Fluids.FLOWING_LAVA);
        super.addExtraFluidTags(tags);
    }

    public List<RecipeInfo<?>> getRecipes() {
        return Recipes_1_12_2.getRecipes();
    }

    public void registerCommands(CommandDispatcher<CommandSource> dispatcher, Set<String> serverCommands) {
        Commands_1_12_2.register(dispatcher, serverCommands);
    }

    @Override
    public boolean shouldBlockChangeReplaceBlockEntity(Block oldBlock, Block newBlock) {
        if (!super.shouldBlockChangeReplaceBlockEntity(oldBlock, newBlock))
            return false;

        if (oldBlock instanceof AbstractSkullBlock && newBlock instanceof AbstractSkullBlock)
            return false;
        if (oldBlock instanceof AbstractBannerBlock && newBlock instanceof AbstractBannerBlock)
            return false;
        if (oldBlock instanceof FlowerPotBlock && newBlock instanceof FlowerPotBlock)
            return false;

        return true;
    }

    @Override
    public void mutateRegistries(RegistryMutator mutator) {
        super.mutateRegistries(mutator);
        // just fucking nuke them all, it's the flattening after all
        mutator.mutate(Protocols.V1_12_2, Registry.BLOCK, Blocks_1_12_2::registerBlocks);
        mutator.mutate(Protocols.V1_12_2, Registry.ITEM, Items_1_12_2::registerItems);
        mutator.mutate(Protocols.V1_12_2, Registry.ENTITY_TYPE, Entities_1_12_2::registerEntities);
        mutator.mutate(Protocols.V1_12_2, Registry.ENCHANTMENT, Enchantments_1_12_2::registerEnchantments);
        mutator.mutate(Protocols.V1_12_2, Registry.POTION, this::mutatePotionRegistry);
        mutator.mutate(Protocols.V1_12_2, Registry.PARTICLE_TYPE, Particles_1_12_2::registerParticles);
        mutator.mutate(Protocols.V1_12_2, Registry.BLOCK_ENTITY_TYPE, BlockEntities_1_12_2::registerBlockEntities);
        mutator.mutate(Protocols.V1_12_2, Registry.STATUS_EFFECT, this::mutateStatusEffectRegistry);
        mutator.mutate(Protocols.V1_12_2, Registry.SOUND_EVENT, this::mutateSoundRegistry);
    }

    @Override
    @ThreadSafe(withGameThread = false)
    public void mutateDynamicRegistries(DynamicRegistryManager.Impl registries) {
        super.mutateDynamicRegistries(registries);
        mutateBiomeRegistry(DefaultDynamicRegistries.getInstance(Registry.BIOME_KEY));
    }

    private void mutatePotionRegistry(ISimpleRegistry<Potion> registry) {
        registry.unregister(Potions.STRONG_SLOWNESS);
        registry.unregister(Potions.TURTLE_MASTER);
        registry.unregister(Potions.LONG_TURTLE_MASTER);
        registry.unregister(Potions.STRONG_TURTLE_MASTER);
        registry.unregister(Potions.SLOW_FALLING);
        registry.unregister(Potions.LONG_SLOW_FALLING);
    }

    @ThreadSafe(withGameThread = false)
    private void mutateBiomeRegistry(DefaultDynamicRegistries<Biome> registry) {
        registry.add("extreme_hills", BiomeKeys.WINDSWEPT_HILLS);
        registry.add("swampland", BiomeKeys.SWAMP);
        registry.add("hell", BiomeKeys.NETHER_WASTES);
        registry.add("sky", BiomeKeys.THE_END);
        registry.add("ice_flats", BiomeKeys.SNOWY_PLAINS);
        registry.add("ice_mountains", Biomes_1_17_1.SNOWY_MOUNTAINS);
        registry.add("mushroom_island", BiomeKeys.MUSHROOM_FIELDS);
        registry.add("mushroom_island_shore", Biomes_1_17_1.MUSHROOM_FIELD_SHORE);
        registry.add("beaches", BiomeKeys.BEACH);
        registry.add("forest_hills", Biomes_1_17_1.WOODED_HILLS);
        registry.add("smaller_extreme_hills", Biomes_1_17_1.MOUNTAIN_EDGE);
        registry.add("stone_beach", BiomeKeys.STONY_SHORE);
        registry.add("cold_beach", BiomeKeys.SNOWY_BEACH);
        registry.add("roofed_forest", BiomeKeys.DARK_FOREST);
        registry.add("taiga_cold", BiomeKeys.SNOWY_TAIGA);
        registry.add("taiga_cold_hills", Biomes_1_17_1.SNOWY_TAIGA_HILLS);
        registry.add("redwood_taiga", BiomeKeys.OLD_GROWTH_PINE_TAIGA);
        registry.add("redwood_taiga_hills", Biomes_1_17_1.GIANT_TREE_TAIGA_HILLS);
        registry.add("extreme_hills_with_trees", BiomeKeys.WINDSWEPT_FOREST);
        registry.add("savanna_rock", BiomeKeys.SAVANNA_PLATEAU);
        registry.add("mesa", BiomeKeys.BADLANDS);
        registry.add("mesa_rock", BiomeKeys.WOODED_BADLANDS);
        registry.add("mesa_clear_rock", Biomes_1_17_1.BADLANDS_PLATEAU);
        registry.add("void", BiomeKeys.THE_VOID);
        registry.add("mutated_plains", BiomeKeys.SUNFLOWER_PLAINS);
        registry.add("mutated_desert", Biomes_1_17_1.DESERT_LAKES);
        registry.add("mutated_extreme_hills", BiomeKeys.WINDSWEPT_GRAVELLY_HILLS);
        registry.add("mutated_forest", BiomeKeys.FLOWER_FOREST);
        registry.add("mutated_taiga", Biomes_1_17_1.TAIGA_MOUNTAINS);
        registry.add("mutated_swampland", Biomes_1_17_1.SWAMP_HILLS);
        registry.add("mutated_ice_flats", BiomeKeys.ICE_SPIKES);
        registry.add("mutated_jungle", Biomes_1_17_1.MODIFIED_JUNGLE);
        registry.add("mutated_jungle_edge", Biomes_1_17_1.MODIFIED_JUNGLE_EDGE);
        registry.add("mutated_birch_forest", BiomeKeys.OLD_GROWTH_BIRCH_FOREST);
        registry.add("mutated_birch_forest_hills", Biomes_1_17_1.TALL_BIRCH_HILLS);
        registry.add("mutated_roofed_forest_hills", Biomes_1_17_1.DARK_FOREST_HILLS);
        registry.add("mutated_taiga_cold", Biomes_1_17_1.SNOWY_TAIGA_MOUNTAINS);
        registry.add("mutated_redwood_taiga", BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA);
        registry.add("mutated_redwood_taiga_hills", Biomes_1_17_1.GIANT_SPRUCE_TAIGA_HILLS);
        registry.add("mutated_extreme_hills_with_trees", Biomes_1_17_1.MODIFIED_GRAVELLY_MOUNTAINS);
        registry.add("mutated_savanna", BiomeKeys.WINDSWEPT_SAVANNA);
        registry.add("mutated_savanna_rock", Biomes_1_17_1.SHATTERED_SAVANNA_PLATEAU);
        registry.add("mutated_mesa", BiomeKeys.ERODED_BADLANDS);
        registry.add("mutated_mesa_rock", Biomes_1_17_1.MODIFIED_WOODED_BADLANDS_PLATEAU);
        registry.add("mutated_mesa_clear_rock", Biomes_1_17_1.MODIFIED_BADLANDS_PLATEAU);
    }

    private void mutateStatusEffectRegistry(ISimpleRegistry<StatusEffect> registry) {
        registry.unregister(StatusEffects.SLOW_FALLING);
        registry.unregister(StatusEffects.CONDUIT_POWER);
        registry.unregister(StatusEffects.DOLPHINS_GRACE);
    }

    private void mutateSoundRegistry(ISimpleRegistry<SoundEvent> registry) {
        registry.unregister(SoundEvents.AMBIENT_UNDERWATER_ENTER);
        registry.unregister(SoundEvents.AMBIENT_UNDERWATER_EXIT);
        registry.unregister(SoundEvents.AMBIENT_UNDERWATER_LOOP);
        registry.unregister(SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS);
        registry.unregister(SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE);
        registry.unregister(SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE);
        registry.unregister(SoundEvents.BLOCK_BEACON_ACTIVATE);
        registry.unregister(SoundEvents.BLOCK_BEACON_AMBIENT);
        registry.unregister(SoundEvents.BLOCK_BEACON_DEACTIVATE);
        registry.unregister(SoundEvents.BLOCK_BEACON_POWER_SELECT);
        registry.unregister(SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP);
        registry.unregister(SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT);
        registry.unregister(SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE);
        registry.unregister(SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT);
        registry.unregister(SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE);
        registry.unregister(SoundEvents.BLOCK_CONDUIT_ACTIVATE);
        registry.unregister(SoundEvents.BLOCK_CONDUIT_AMBIENT);
        registry.unregister(SoundEvents.BLOCK_CONDUIT_AMBIENT_SHORT);
        registry.unregister(SoundEvents.BLOCK_CONDUIT_ATTACK_TARGET);
        registry.unregister(SoundEvents.BLOCK_CONDUIT_DEACTIVATE);
        registry.unregister(SoundEvents.BLOCK_WET_GRASS_BREAK);
        registry.unregister(SoundEvents.BLOCK_WET_GRASS_FALL);
        registry.unregister(SoundEvents.BLOCK_WET_GRASS_HIT);
        registry.unregister(SoundEvents.BLOCK_WET_GRASS_PLACE);
        registry.unregister(SoundEvents.BLOCK_WET_GRASS_STEP);
        registry.unregister(SoundEvents.BLOCK_CORAL_BLOCK_BREAK);
        registry.unregister(SoundEvents.BLOCK_CORAL_BLOCK_FALL);
        registry.unregister(SoundEvents.BLOCK_CORAL_BLOCK_HIT);
        registry.unregister(SoundEvents.BLOCK_CORAL_BLOCK_PLACE);
        registry.unregister(SoundEvents.BLOCK_CORAL_BLOCK_STEP);
        registry.unregister(SoundEvents.BLOCK_PUMPKIN_CARVE);
        registry.unregister(SoundEvents.ENTITY_COD_AMBIENT);
        registry.unregister(SoundEvents.ENTITY_COD_DEATH);
        registry.unregister(SoundEvents.ENTITY_COD_FLOP);
        registry.unregister(SoundEvents.ENTITY_COD_HURT);
        registry.unregister(SoundEvents.ENTITY_DOLPHIN_AMBIENT);
        registry.unregister(SoundEvents.ENTITY_DOLPHIN_AMBIENT_WATER);
        registry.unregister(SoundEvents.ENTITY_DOLPHIN_ATTACK);
        registry.unregister(SoundEvents.ENTITY_DOLPHIN_DEATH);
        registry.unregister(SoundEvents.ENTITY_DOLPHIN_EAT);
        registry.unregister(SoundEvents.ENTITY_DOLPHIN_HURT);
        registry.unregister(SoundEvents.ENTITY_DOLPHIN_JUMP);
        registry.unregister(SoundEvents.ENTITY_DOLPHIN_PLAY);
        registry.unregister(SoundEvents.ENTITY_DOLPHIN_SPLASH);
        registry.unregister(SoundEvents.ENTITY_DOLPHIN_SWIM);
        registry.unregister(SoundEvents.ENTITY_DROWNED_AMBIENT);
        registry.unregister(SoundEvents.ENTITY_DROWNED_AMBIENT_WATER);
        registry.unregister(SoundEvents.ENTITY_DROWNED_DEATH);
        registry.unregister(SoundEvents.ENTITY_DROWNED_DEATH_WATER);
        registry.unregister(SoundEvents.ENTITY_DROWNED_HURT);
        registry.unregister(SoundEvents.ENTITY_DROWNED_HURT_WATER);
        registry.unregister(SoundEvents.ENTITY_DROWNED_SHOOT);
        registry.unregister(SoundEvents.ENTITY_DROWNED_STEP);
        registry.unregister(SoundEvents.ENTITY_DROWNED_SWIM);
        registry.unregister(SoundEvents.ENTITY_FISH_SWIM);
        registry.unregister(SoundEvents.ENTITY_HUSK_CONVERTED_TO_ZOMBIE);
        registry.unregister(SoundEvents.ENTITY_PARROT_IMITATE_DROWNED);
        registry.unregister(SoundEvents.ENTITY_PARROT_IMITATE_PHANTOM);
        registry.unregister(SoundEvents.ENTITY_PHANTOM_AMBIENT);
        registry.unregister(SoundEvents.ENTITY_PHANTOM_BITE);
        registry.unregister(SoundEvents.ENTITY_PHANTOM_DEATH);
        registry.unregister(SoundEvents.ENTITY_PHANTOM_FLAP);
        registry.unregister(SoundEvents.ENTITY_PHANTOM_HURT);
        registry.unregister(SoundEvents.ENTITY_PHANTOM_SWOOP);
        registry.unregister(SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED);
        registry.unregister(SoundEvents.ENTITY_PUFFER_FISH_AMBIENT);
        registry.unregister(SoundEvents.ENTITY_PUFFER_FISH_BLOW_OUT);
        registry.unregister(SoundEvents.ENTITY_PUFFER_FISH_BLOW_UP);
        registry.unregister(SoundEvents.ENTITY_PUFFER_FISH_DEATH);
        registry.unregister(SoundEvents.ENTITY_PUFFER_FISH_FLOP);
        registry.unregister(SoundEvents.ENTITY_PUFFER_FISH_HURT);
        registry.unregister(SoundEvents.ENTITY_PUFFER_FISH_STING);
        registry.unregister(SoundEvents.ENTITY_SALMON_AMBIENT);
        registry.unregister(SoundEvents.ENTITY_SALMON_DEATH);
        registry.unregister(SoundEvents.ENTITY_SALMON_FLOP);
        registry.unregister(SoundEvents.ENTITY_SALMON_HURT);
        registry.unregister(SoundEvents.ENTITY_SKELETON_HORSE_SWIM);
        registry.unregister(SoundEvents.ENTITY_SKELETON_HORSE_AMBIENT_WATER);
        registry.unregister(SoundEvents.ENTITY_SKELETON_HORSE_GALLOP_WATER);
        registry.unregister(SoundEvents.ENTITY_SKELETON_HORSE_JUMP_WATER);
        registry.unregister(SoundEvents.ENTITY_SKELETON_HORSE_STEP_WATER);
        registry.unregister(SoundEvents.ENTITY_SQUID_SQUIRT);
        registry.unregister(SoundEvents.ENTITY_TROPICAL_FISH_AMBIENT);
        registry.unregister(SoundEvents.ENTITY_TROPICAL_FISH_DEATH);
        registry.unregister(SoundEvents.ENTITY_TROPICAL_FISH_FLOP);
        registry.unregister(SoundEvents.ENTITY_TROPICAL_FISH_HURT);
        registry.unregister(SoundEvents.ENTITY_TURTLE_AMBIENT_LAND);
        registry.unregister(SoundEvents.ENTITY_TURTLE_DEATH);
        registry.unregister(SoundEvents.ENTITY_TURTLE_DEATH_BABY);
        registry.unregister(SoundEvents.ENTITY_TURTLE_EGG_BREAK);
        registry.unregister(SoundEvents.ENTITY_TURTLE_EGG_CRACK);
        registry.unregister(SoundEvents.ENTITY_TURTLE_EGG_HATCH);
        registry.unregister(SoundEvents.ENTITY_TURTLE_HURT);
        registry.unregister(SoundEvents.ENTITY_TURTLE_HURT_BABY);
        registry.unregister(SoundEvents.ENTITY_TURTLE_LAY_EGG);
        registry.unregister(SoundEvents.ENTITY_TURTLE_SHAMBLE);
        registry.unregister(SoundEvents.ENTITY_TURTLE_SHAMBLE_BABY);
        registry.unregister(SoundEvents.ENTITY_TURTLE_SWIM);
        registry.unregister(SoundEvents.ENTITY_ZOMBIE_CONVERTED_TO_DROWNED);
        registry.unregister(SoundEvents.ENTITY_ZOMBIE_DESTROY_EGG);
        registry.unregister(SoundEvents.ITEM_ARMOR_EQUIP_TURTLE);
        registry.unregister(SoundEvents.ITEM_AXE_STRIP);
        registry.unregister(SoundEvents.ITEM_BUCKET_EMPTY_FISH);
        registry.unregister(SoundEvents.ITEM_BUCKET_FILL_FISH);
        registry.unregister(SoundEvents.ITEM_TRIDENT_HIT);
        registry.unregister(SoundEvents.ITEM_TRIDENT_HIT_GROUND);
        registry.unregister(SoundEvents.ITEM_TRIDENT_RETURN);
        registry.unregister(SoundEvents.ITEM_TRIDENT_RIPTIDE_1);
        registry.unregister(SoundEvents.ITEM_TRIDENT_RIPTIDE_2);
        registry.unregister(SoundEvents.ITEM_TRIDENT_RIPTIDE_3);
        registry.unregister(SoundEvents.ITEM_TRIDENT_THROW);
        registry.unregister(SoundEvents.ITEM_TRIDENT_THUNDER);
        registry.unregister(SoundEvents.MUSIC_UNDER_WATER);
    }
}
