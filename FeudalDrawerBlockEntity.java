package com.immersivestorage.block;

import com.immersivestorage.ImmersiveStorageMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Almacena UN solo tipo de ítem (con su NBT) hasta MAX_STACKS stacks.
 */
public class FeudalDrawerBlockEntity extends BlockEntity {
    public static final int MAX_STACKS = 32;
    private static final int EXTRACT_COOLDOWN_TICKS = 3;

    /** Ítem almacenado (siempre count = 1) o EMPTY. */
    private ItemStack template = ItemStack.EMPTY;
    private int count = 0;
    private long lastExtractTime = -100L; // no se guarda

    public FeudalDrawerBlockEntity(BlockPos pos, BlockState state) {
        super(ImmersiveStorageMod.FEUDAL_DRAWER_BE, pos, state);
    }

    // ---------------------------------------------------------------- estado

    public boolean isEmpty() {
        return count <= 0 || template.isEmpty();
    }

    public ItemStack getDisplayStack() {
        return template;
    }

    public int getCount() {
        return count;
    }

    public int capacity() {
        return template.getMaxCount() * MAX_STACKS;
    }

    public boolean holds(net.minecraft.item.Item item) {
        return !isEmpty() && template.isOf(item);
    }

    // ------------------------------------------------------------- operaciones

    /** Inserta lo que quepa de {@code stack} (lo reduce) y devuelve cuántos ítems entraron. */
    public int insert(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (isEmpty()) {
            template = stack.copy();
            template.setCount(1);
            count = 0;
        } else if (!ItemStack.canCombine(template, stack)) {
            return 0;
        }
        int moved = Math.min(capacity() - count, stack.getCount());
        if (moved <= 0) {
            return 0;
        }
        count += moved;
        stack.decrement(moved);
        sync();
        return moved;
    }

    /** Saca hasta {@code amount} ítems. */
    public ItemStack extract(int amount) {
        if (isEmpty()) {
            return ItemStack.EMPTY;
        }
        int n = Math.min(amount, count);
        ItemStack out = template.copy();
        out.setCount(n);
        count -= n;
        if (count <= 0) {
            count = 0;
            template = ItemStack.EMPTY;
        }
        sync();
        return out;
    }

    /** Clic derecho. Mano con ítem: deposita ese stack. Mano vacía: deposita todo lo igual del inventario. */
    public void depositFrom(PlayerEntity player) {
        ItemStack held = player.getMainHandStack();
        int moved = 0;

        if (!held.isEmpty()) {
            if (!isEmpty() && !ItemStack.canCombine(template, held)) {
                notify(player, "message.immersivestorage.drawer.mismatch");
                return;
            }
            if (!isEmpty() && count >= capacity()) {
                notify(player, "message.immersivestorage.drawer.full");
                return;
            }
            moved = insert(held);
        } else if (!isEmpty()) {
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < inv.main.size() && count < capacity(); i++) {
                ItemStack s = inv.main.get(i);
                if (!s.isEmpty() && ItemStack.canCombine(template, s)) {
                    moved += insert(s);
                }
            }
        }

        if (moved > 0 && world != null) {
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 0.6f, 1.0f);
        }
    }

    /** Clic izquierdo: 1 ítem, o 1 stack completo con Shift. */
    public void extractTo(PlayerEntity player, boolean fullStack) {
        if (world == null || isEmpty()) {
            return;
        }
        long now = world.getTime();
        if (now - lastExtractTime < EXTRACT_COOLDOWN_TICKS) {
            return; // evita vaciarlo en un tick al mantener pulsado
        }
        lastExtractTime = now;

        ItemStack out = extract(fullStack ? template.getMaxCount() : 1);
        if (!out.isEmpty()) {
            player.getInventory().offerOrDrop(out);
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.4f, 1.2f);
        }
    }

    public void dropContents(World world, BlockPos pos) {
        while (!isEmpty()) {
            ItemStack out = extract(template.getMaxCount());
            ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, out);
        }
    }

    private static void notify(PlayerEntity player, String key) {
        if (player instanceof ServerPlayerEntity sp) {
            sp.sendMessage(Text.translatable(key), true);
        }
    }

    // --------------------------------------------------------- persistencia/red

    private void sync() {
        markDirty();
        if (world != null && !world.isClient) {
            BlockState state = getCachedState();
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.put("Item", template.writeNbt(new NbtCompound()));
        nbt.putInt("Count", count);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        template = ItemStack.fromNbt(nbt.getCompound("Item"));
        count = template.isEmpty() ? 0 : nbt.getInt("Count");
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }
}
