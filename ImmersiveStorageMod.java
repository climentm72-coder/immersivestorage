package com.immersivestorage;

import com.immersivestorage.block.FeudalDrawerBlock;
import com.immersivestorage.block.FeudalDrawerBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImmersiveStorageMod implements ModInitializer {
    public static final String MOD_ID = "immersivestorage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Block FEUDAL_DRAWER = new FeudalDrawerBlock(
            AbstractBlock.Settings.of(Material.WOOD)
                    .strength(2.5f)
                    .sounds(BlockSoundGroup.WOOD));

    public static final Item FEUDAL_DRAWER_ITEM = new BlockItem(FEUDAL_DRAWER, new Item.Settings());

    public static BlockEntityType<FeudalDrawerBlockEntity> FEUDAL_DRAWER_BE;

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        // Bloque, item y BlockEntity
        Registry.register(Registries.BLOCK, id("feudal_drawer"), FEUDAL_DRAWER);
        Registry.register(Registries.ITEM, id("feudal_drawer"), FEUDAL_DRAWER_ITEM);
        FEUDAL_DRAWER_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                id("feudal_drawer"),
                BlockEntityType.Builder.create(FeudalDrawerBlockEntity::new, FEUDAL_DRAWER).build(null));

        // Pestaña creativa
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL)
                .register(entries -> entries.add(FEUDAL_DRAWER_ITEM));

        // Clic izquierdo: extraer (se ejecuta en cliente y servidor)
        AttackBlockCallback.EVENT.register(ImmersiveStorageMod::onAttackBlock);

        // Red + escaneo del resaltado inteligente (lado servidor)
        StorageHighlightHandler.registerServer();

        LOGGER.info("Immersive Storage & Smart Drawers loaded.");
    }

    private static ActionResult onAttackBlock(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
        if (player.isSpectator() || player.isCreative()) {
            return ActionResult.PASS; // creativo: romper el bloque con normalidad
        }
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof FeudalDrawerBlockEntity drawer) || drawer.isEmpty()) {
            return ActionResult.PASS; // cajón vacío: se rompe con normalidad
        }
        if (world.isClient) {
            return ActionResult.SUCCESS; // evita iniciar el picado en el cliente
        }
        drawer.extractTo(player, player.isSneaking());
        return ActionResult.SUCCESS;
    }
}
