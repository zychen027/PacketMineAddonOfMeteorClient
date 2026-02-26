package com.packetmine.addon.tool

import com.packetmine.addon.MineTarget
import com.packetmine.addon.PacketMineModule
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair
import meteordevelopment.meteorclient.systems.modules.Modules
import meteordevelopment.meteorclient.systems.modules.world.AutoTool
import meteordevelopment.meteorclient.utils.player.ChatUtils
import net.minecraft.block.BlockState
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.effect.StatusEffectUtil
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.BlockPos

/**
 * 工具切换模式基类
 * 
 * 定义何时以及如何切换到合适的工具
 * 包含挖掘速度计算逻辑
 */
abstract class MineToolMode(
    val name: String,
    val syncOnStart: Boolean = false,
    private val switchesNever: Boolean = false
) {
    
    /**
     * 是否应该切换工具
     */
    abstract fun shouldSwitch(mineTarget: MineTarget): Boolean
    
    /**
     * 获取切换方法
     */
    open fun getSwitchingMethod(): SwitchMethod = SwitchMethod.Normal
    
    /**
     * 计算挖掘进度增量
     */
    fun getBlockBreakingDelta(pos: BlockPos, state: BlockState, itemStack: ItemStack?): Float {
        if (switchesNever || itemStack == null) {
            return state.calcBlockBreakingDelta(
                PacketMineModule.mc.player!!,
                PacketMineModule.mc.world!!,
                pos
            )
        }
        
        return calcBlockBreakingDelta(pos, state, itemStack)
    }
    
    /**
     * 获取最佳工具槽位
     */
    fun getSlot(state: BlockState): IntObjectImmutablePair<ItemStack>? {
        if (switchesNever) {
            return null
        }
        
        val autoTool = Modules.get().get(AutoTool::class.java)
        if (autoTool != null && autoTool.isActive) {
            // 使用 AutoTool 的选择逻辑
            val bestStack = findBestTool(state)
            return bestStack?.let { 
                IntObjectImmutablePair(findSlotForStack(it), it) 
            }
        }
        
        return null
    }
    
    /**
     * 切换工具
     */
    fun switch(slot: IntObjectImmutablePair<ItemStack>?, mineTarget: MineTarget) {
        if (slot == null) return
        
        if (shouldSwitch(mineTarget)) {
            getSwitchingMethod().switch(slot, mineTarget)
        }
    }
    
    /**
     * 查找最佳工具
     */
    private fun findBestTool(state: BlockState): ItemStack? {
        val player = PacketMineModule.mc.player ?: return null
        var bestStack: ItemStack? = null
        var bestSpeed = 0f
        
        for (i in 0..8) {
            val stack = player.inventory.getStack(i)
            val speed = calcMiningSpeed(state, stack)
            if (speed > bestSpeed) {
                bestSpeed = speed
                bestStack = stack
            }
        }
        
        return bestStack
    }
    
    /**
     * 查找物品所在槽位
     */
    private fun findSlotForStack(stack: ItemStack): Int {
        val player = PacketMineModule.mc.player ?: return 0
        for (i in 0..8) {
            if (player.inventory.getStack(i) == stack) {
                return i
            }
        }
        return 0
    }
    
    // ========== 挖掘速度计算（移植自 Minecraft） ==========
    
    /**
     * 计算挖掘速度
     */
    private fun calcMiningSpeed(state: BlockState, stack: ItemStack): Float {
        var speed = stack.getMiningSpeedMultiplier(state)
        
        // 效率附魔加成
        val efficiencyLevel = EnchantmentHelper.getLevel(
            Enchantments.EFFICIENCY,
            stack
        )
        
        if (speed > 1f && efficiencyLevel > 0) {
            speed += efficiencyLevel * efficiencyLevel + 1f
        }
        
        // 急迫效果加成
        val player = PacketMineModule.mc.player!!
        if (StatusEffectUtil.hasHaste(player)) {
            speed *= 1f + (StatusEffectUtil.getHasteAmplifier(player) + 1) * 0.2f
        }
        
        // 挖掘疲劳减益
        if (player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            val amplifier = player.getStatusEffect(StatusEffects.MINING_FATIGUE)!!.amplifier
            val multiplier = when (amplifier) {
                0 -> 0.3f
                1 -> 0.09f
                2 -> 0.0027f
                else -> 8.1E-4f
            }
            speed *= multiplier
        }
        
        // 水下挖掘
        if (player.isSubmergedIn(FluidTags.WATER)) {
            speed *= 5f // 简化处理
        }
        
        // 空中挖掘惩罚
        if (!player.isOnGround) {
            speed /= 5f
        }
        
        return speed
    }
    
    /**
     * 计算方块破坏增量
     */
    private fun calcBlockBreakingDelta(pos: BlockPos, state: BlockState, stack: ItemStack): Float {
        val world = PacketMineModule.mc.world!!
        val hardness = state.getHardness(world, pos)
        
        if (hardness == -1f) {
            return 0f
        }
        
        val suitableMultiplier = if (!state.isToolRequired || stack.isSuitableFor(state)) 30 else 100
        return calcMiningSpeed(state, stack) / hardness / suitableMultiplier
    }
    
    override fun toString(): String = name
}
