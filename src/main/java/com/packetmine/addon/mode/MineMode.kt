package com.packetmine.addon.mode

import com.packetmine.addon.MineTarget
import com.packetmine.addon.PacketMineModule
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair
import meteordevelopment.meteorclient.utils.world.getBlockState
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

/**
 * 挖掘模式基类
 * 定义不同挖掘行为的接口
 */
abstract class MineMode(
    val name: String,
    val canManuallyChange: Boolean = true,
    val canAbort: Boolean = true,
    val stopOnStateChange: Boolean = true
) {
    
    /**
     * 检查目标是否无效
     */
    open fun isInvalid(mineTarget: MineTarget, state: BlockState): Boolean {
        val world = PacketMineModule.mc.world ?: return true
        val player = PacketMineModule.mc.player ?: return true
        
        // 检查方块是否不可破坏
        val hardness = state.getHardness(world, mineTarget.targetPos)
        val unbreakable = hardness < 0
        
        return (unbreakable && !player.isCreative) || state.isAir
    }
    
    /**
     * 检查是否应该以此方块为目标
     */
    open fun shouldTarget(blockPos: BlockPos, state: BlockState): Boolean {
        val world = PacketMineModule.mc.world ?: return false
        val hardness = state.getHardness(world, blockPos)
        return hardness >= 0
    }
    
    /**
     * 当无法看向目标时调用
     */
    open fun onCannotLookAtTarget(mineTarget: MineTarget) {}
    
    /**
     * 开始挖掘
     */
    abstract fun start(mineTarget: MineTarget)
    
    /**
     * 完成挖掘
     */
    abstract fun finish(mineTarget: MineTarget)
    
    /**
     * 是否应该更新挖掘进度
     */
    abstract fun shouldUpdate(
        mineTarget: MineTarget,
        slot: IntObjectImmutablePair<ItemStack>?
    ): Boolean
    
    override fun toString(): String = name
}
