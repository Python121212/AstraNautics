package com.astranautics.block;

import com.astranautics.registry.ModBlocks;
import com.astranautics.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * ノズル金型（Nozzle Mold）。
 *
 * <p>「ノズルを自分で作る」ための装置です。金型に真鍮（または銅）のインゴットを
 * 1 個入れて右クリックすると、ノズルが 1 個押し出されます。
 * 金型そのものは耐久が無く、置いたまま何度でも使えます。</p>
 *
 * <pre>
 *   ノズル金型 = 鉄 4 + 石 4 + 真鍮 1（レシピ）
 *   金型にインゴットを入れて右クリック → ノズル 1 個（インゴット 1 個消費）
 * </pre>
 */
public class NozzleMoldBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** 押し出し中（モデルの演出用）。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public NozzleMoldBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack nozzle = new ItemStack(ModItems.enginePart(com.astranautics.engine.EnginePart.NOZZLE));

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // 金属インゴットを持っていればノズルを押し出す
        boolean isMetal = held.is(net.minecraft.tags.ItemTags.create(
                new net.minecraft.resources.ResourceLocation("forge", "ingots/brass")))
                || held.is(net.minecraft.tags.ItemTags.create(
                new net.minecraft.resources.ResourceLocation("forge", "ingots/copper")))
                || held.is(net.minecraft.tags.ItemTags.create(
                new net.minecraft.resources.ResourceLocation("forge", "ingots/iron")));

        if (isMetal) {
            held.shrink(1);
            if (!player.getInventory().add(nozzle)) {
                player.drop(nozzle, false);
            }
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7F, 1.4F);
            player.displayClientMessage(Component.translatable("message.astranautics.nozzle_pressed"), true);
            // 押し出しの演出
            level.setBlock(pos, state.setValue(ACTIVE, true), 3);
            level.scheduleTick(pos, this, 10);
            return InteractionResult.CONSUME;
        }

        // 何も持っていない → 使い方と必要素材を表示
        player.displayClientMessage(Component.translatable("message.astranautics.nozzle_mold_help"), false);
        // 金型自体を回収する（スニーク時）
        if (player.isShiftKeyDown()) {
            level.removeBlock(pos, false);
            if (!player.getInventory().add(new ItemStack(ModBlocks.NOZZLE_MOLD.get()))) {
                player.drop(new ItemStack(ModBlocks.NOZZLE_MOLD.get()), false);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
                     net.minecraft.util.RandomSource random) {
        if (state.getValue(ACTIVE)) {
            level.setBlock(pos, state.setValue(ACTIVE, false), 3);
        }
    }

    /** 押し出し方向（プレイヤーの方を向く）。 */
    public static Direction facingFor(Player player) {
        return player.getDirection().getOpposite();
    }
}
