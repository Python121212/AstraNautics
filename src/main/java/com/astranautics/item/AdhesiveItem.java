package com.astranautics.item;

import com.astranautics.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 接着剤（AstraNautics Adhesive）。
 *
 * <p>「範囲を選んでまとめて物理化」するためのアイテムです。操作は 3 段階：</p>
 *
 * <ol>
 *   <li><b>右クリック（1 回押すだけ）</b> … 見ているブロックを基準に範囲を自動選択（3×3 など）</li>
 *   <li><b>左クリック</b> … 選択範囲を確定して接着（マーカーが付く）</li>
 *   <li><b>物理化装置を ON</b> … 接着された範囲が <b>まとめて物理化</b></li>
 * </ol>
 *
 * <p>接着は「ブロック同士の結合」として NBT に保存され、隣接していなくても
 * 同じクラスタ（1 つの剛体）として扱われます。将来の「船 = 1 剛体」の土台です。</p>
 */
public class AdhesiveItem extends Item {

    /** アイテムに保存する接着グループのタグ。 */
    public static final String TAG_GROUPS = "AdhesiveGroups";
    private static final String TAG_SELECTION = "AdhesiveSelection";
    private static final String TAG_MODE = "AdhesiveRadius";

    /** 範囲選択のプリセット（半径 → 表示名）。 */
    private static final int[] RADII = {0, 1, 2, 3, 4};
    private static final String[] RADIUS_NAMES = {"1x1", "3x3", "5x5", "7x7", "9x9"};

    public AdhesiveItem(Properties properties) {
        super(properties);
    }

    /* ------------------------------------------------------------------ */
    /* 範囲の選択                                                          */
    /* ------------------------------------------------------------------ */

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }

        // スニーク + 右クリック → 範囲サイズを切り替え
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                int next = (getRadius(stack) + 1) % RADII.length;
                stack.getOrCreateTag().putInt(TAG_MODE, next);
                player.displayClientMessage(Component.translatable(
                                "message.astranautics.adhesive_size", RADIUS_NAMES[next])
                        .withStyle(ChatFormatting.AQUA), true);
                level.playSound(null, context.getClickedPos(), SoundEvents.UI_BUTTON_CLICK.get(),
                        SoundSource.PLAYERS, 0.5F, 1.6F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // 右クリック 1 回 → 範囲を選択（長押し不要）
        BlockPos center = context.getClickedPos();
        int radius = getRadius(stack);
        List<BlockPos> selection = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getFluidState().isEmpty() == false) {
                continue;
            }
            if (com.astranautics.block.PhysicalizedBlock.isUnsupported(state)
                    || com.astranautics.physics.Compat.isBlocked(state)) {
                continue;
            }
            selection.add(pos.immutable());
        }

        if (selection.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.astranautics.adhesive_empty")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (selection.size() > 512) {
            selection = new ArrayList<>(selection.subList(0, 512));
        }

        if (!level.isClientSide) {
            saveSelection(stack, selection);
            player.displayClientMessage(Component.translatable(
                            "message.astranautics.adhesive_selected", selection.size())
                    .withStyle(ChatFormatting.YELLOW), true);
            level.playSound(null, center, SoundEvents.NOTE_BLOCK_PLING.value(),
                    SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && !level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.astranautics.adhesive_help")
                    .withStyle(ChatFormatting.GRAY), false);
        } else if (!level.isClientSide && !getSelection(stack).isEmpty()) {
            // 空中で右クリック → 選択解除
            clearSelection(stack);
            player.displayClientMessage(Component.translatable("message.astranautics.adhesive_cleared")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /* ------------------------------------------------------------------ */
    /* 左クリックで確定（接着）                                             */
    /* ------------------------------------------------------------------ */

    /** サーバー側：ネットワークから受け取った選択を確定する。 */
    public static void confirmOnServer(Player player, ItemStack stack, List<BlockPos> selection) {
        if (player.level().isClientSide || selection.isEmpty()) {
            return;
        }
        addGroup(stack, selection);
        player.displayClientMessage(Component.translatable(
                        "message.astranautics.adhesive_confirmed", selection.size())
                .withStyle(ChatFormatting.AQUA), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SLIME_BLOCK_PLACE,
                SoundSource.PLAYERS, 0.7F, 1.3F);
    }

    /** クライアントの左クリック（空中 or ブロック）から呼ばれる。 */
    public static void confirmSelection(Player player, ItemStack stack) {
        Level level = player.level();
        List<BlockPos> selection = getSelection(stack);
        if (selection.isEmpty()) {
            return;
        }
        if (!level.isClientSide) {
            addGroup(stack, selection);
            player.displayClientMessage(Component.translatable(
                            "message.astranautics.adhesive_confirmed", selection.size())
                    .withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, player.blockPosition(), SoundEvents.SLIME_BLOCK_PLACE,
                    SoundSource.PLAYERS, 0.7F, 1.3F);
            clearSelection(stack);
            return;
        }
        // クライアントはサーバーへ確定を依頼する
        com.astranautics.network.ModNetwork.sendConfirmSelection();
        clearSelection(stack);
    }

    /* ------------------------------------------------------------------ */
    /* 接着グループの保存                                                   */
    /* ------------------------------------------------------------------ */

    /** 接着したグループ（ブロック座標のリスト）をすべて取り出す。 */
    public static List<List<BlockPos>> getGroups(ItemStack stack) {
        List<List<BlockPos>> groups = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_GROUPS)) {
            return groups;
        }
        ListTag list = tag.getList(TAG_GROUPS, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag groupTag = list.getCompound(i);
            ListTag positions = groupTag.getList("Positions", net.minecraft.nbt.Tag.TAG_COMPOUND);
            List<BlockPos> group = new ArrayList<>(positions.size());
            for (int j = 0; j < positions.size(); j++) {
                CompoundTag posTag = positions.getCompound(j);
                group.add(new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z")));
            }
            if (!group.isEmpty()) {
                groups.add(group);
            }
        }
        return groups;
    }

    private static void addGroup(ItemStack stack, List<BlockPos> group) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag list = tag.getList(TAG_GROUPS, net.minecraft.nbt.Tag.TAG_COMPOUND);
        CompoundTag groupTag = new CompoundTag();
        ListTag positions = new ListTag();
        for (BlockPos pos : group) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", pos.getX());
            posTag.putInt("Y", pos.getY());
            posTag.putInt("Z", pos.getZ());
            positions.add(posTag);
        }
        groupTag.put("Positions", positions);
        list.add(groupTag);
        tag.put(TAG_GROUPS, list);
    }

    /** 接着グループの合計ブロック数（ツールチップ用）。 */
    public static int totalGluedBlocks(ItemStack stack) {
        int total = 0;
        for (List<BlockPos> group : getGroups(stack)) {
            total += group.size();
        }
        return total;
    }

    /* ------------------------------------------------------------------ */
    /* 選択状態                                                             */
    /* ------------------------------------------------------------------ */

    public static List<BlockPos> getSelection(ItemStack stack) {
        List<BlockPos> selection = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SELECTION)) {
            return selection;
        }
        ListTag positions = tag.getList(TAG_SELECTION, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < positions.size(); i++) {
            CompoundTag posTag = positions.getCompound(i);
            selection.add(new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z")));
        }
        return selection;
    }

    /** クライアントの選択プレビュー用。 */
    public static List<BlockPos> getSelectionForRender(ItemStack stack) {
        return getSelection(stack);
    }

    private static void saveSelection(ItemStack stack, List<BlockPos> selection) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag positions = new ListTag();
        for (BlockPos pos : selection) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", pos.getX());
            posTag.putInt("Y", pos.getY());
            posTag.putInt("Z", pos.getZ());
            positions.add(posTag);
        }
        tag.put(TAG_SELECTION, positions);
    }

    public static void clearSelection(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_SELECTION);
        }
    }

    public static int getRadius(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 1 : Math.max(0, Math.min(RADII.length - 1, tag.getInt(TAG_MODE)));
    }

    public static String radiusName(ItemStack stack) {
        return RADIUS_NAMES[getRadius(stack)];
    }

    /* ------------------------------------------------------------------ */
    /* 物理化（まとめて）                                                   */
    /* ------------------------------------------------------------------ */

    /**
     * 接着グループをまとめて物理化する（全グループ or 指定位置を含むグループ）。
     *
     * @return 物理化したブロック数
     */
    public static int physicalizeGluedGroup(Level level, ItemStack stack, @Nullable BlockPos near) {
        int count = 0;
        for (List<BlockPos> group : getGroups(stack)) {
            if (near != null && !containsNear(group, near, 4)) {
                continue;
            }
            for (BlockPos pos : group) {
                if (com.astranautics.block.PhysicalizedBlock.physicalize(level, pos)) {
                    count++;
                }
            }
            // 接着グループを 1 つのクラスタとして再構築
            for (BlockPos pos : group) {
                com.astranautics.physics.ClusterManager.rebuild(level, pos);
            }
        }
        return count;
    }

    private static boolean containsNear(List<BlockPos> group, BlockPos pos, int distance) {
        for (BlockPos p : group) {
            if (p.distManhattan(pos) <= distance * 2) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------ */
    /* ツールチップ                                                         */
    /* ------------------------------------------------------------------ */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.astranautics.adhesive.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.astranautics.adhesive.2").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.astranautics.adhesive.3").withStyle(ChatFormatting.DARK_AQUA));
        int selected = getSelection(stack).size();
        if (selected > 0) {
            tooltip.add(Component.translatable("tooltip.astranautics.adhesive.selected", selected)
                    .withStyle(ChatFormatting.AQUA));
        }
        int glued = totalGluedBlocks(stack);
        if (glued > 0) {
            tooltip.add(Component.translatable("tooltip.astranautics.adhesive.glued", glued)
                    .withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable("tooltip.astranautics.ponder_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** テスト・デバッグ用：状態の要約。 */
    public static String debugSummary(ItemStack stack) {
        List<List<BlockPos>> groups = getGroups(stack);
        StringBuilder sb = new StringBuilder();
        sb.append("選択=").append(getSelection(stack).size())
                .append(" グループ=").append(groups.size()).append(" [");
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(groups.get(i).size());
        }
        sb.append("] 範囲=").append(radiusName(stack));
        return sb.toString();
    }

    /** 参照用（未使用警告避け）。 */
    public static BlockState stateAt(Level level, BlockPos pos) {
        return level.getBlockState(pos);
    }
}
