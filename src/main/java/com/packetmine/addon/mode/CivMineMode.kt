package com.packetmine.addon.mode

import com.packetmine.addon.MineTarget
import com.packetmine.addon.PacketMineModule
import com.packetmine.addon.tool.MineToolMode
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair
import meteordevelopment.meteorclient.systems.modules.Modules
import meteordevelopment.meteorclient.systems.modules.world.AutoTool
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

/**
 * Civ 挖掘模式
 * 
 * 特点：
 * - 持续发送 STOP_DESTROY_BLOCK 包以保持目标
 * - 适用于某些反作弊系统
 * - 不等待服务器确认
 */
object CivMineMode : MineMode("Civ", stopOnStateChange = false) {
    
    private var shouldSwitch = false
    
    override fun isInvalid(mineTarget: MineTarget, state: BlockState): Boolean {
        val world = PacketMineModule.mc.world ?: return true
        val player = PacketMineModule.mc.player ?: return true
        
        // Civ 模式对硬度为 1 的方块有特殊处理
        val hardness = state.getHardness(world, mineTarget.targetPos)
        return hardness == 1f && !player.isCreative
    }
    
    override fun onCannotLookAtTarget(mineTarget: MineTarget) {
        // 发送保持目标的包
        val packet = PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
            mineTarget.targetPos,
            Direction.DOWN,
            0
        )
        
        PacketMineModule.mc.networkHandler?.sendPacket(packet)
    }
    
    override fun shouldTarget(blockPos: BlockPos, state: BlockState): Boolean {
        val world = PacketMineModule.mc.world ?: return false
        val hardness = state.getHardness(world, blockPos)
        return hardness > 0f
    }
    
    override fun start(mineTarget: MineTarget) {
        // 使用标准模式的开始逻辑
        NormalMineMode.start(mineTarget)
    }
    
    override fun finish(mineTarget: MineTarget) {
        // 发送停止挖掘包
        val packet = PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
            mineTarget.targetPos,
            mineTarget.direction!!,
            0
        )
        
        PacketMineModule.mc.networkHandler?.sendPacket(packet)
        
        // 挥动手臂
        PacketMineModule.mc.player?.swingHand(Hand.MAIN_HAND)
        
        mineTarget.finished = true
    }
    
    override fun shouldUpdate(mineTarget: MineTarget, slot: IntObjectImmutablePair<ItemStack>?): Boolean {
        if (!mineTarget.finished) {
            return true
        }
        
        // 某些方块需要持有特定工具才能破坏
        val player = PacketMineModule.mc.player ?: return false
        val world = PacketMineModule.mc.world ?: return false
        val oldSlot = player.inventory.selectedSlot
        val state = world.getBlockState(mineTarget.targetPos)
        
        var shouldSwitchNow = shouldSwitch && state.isToolRequired
        
        if (shouldSwitchNow) {
            // 尝试使用 AutoTool
            val autoTool = Modules.get().get(AutoTool::class.java)
            if (autoTool != null && autoTool.isActive) {
                autoTool.switchToBreakBlock(mineTarget.targetPos)
                shouldSwitchNow = false
            } else {
                // 手动选择最佳工具
                val bestSlot = findBestToolSlot(state)
                if (bestSlot != null && bestSlot != oldSlot) {
                    val switchPacket = UpdateSelectedSlotC2SPacket(bestSlot)
                    PacketMineModule.mc.networkHandler?.sendPacket(switchPacket)
                } else {
                    shouldSwitchNow = false
                }
            }
        }
        
        // 持续发送停止挖掘包
        val packet = PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
            mineTarget.targetPos,
            mineTarget.direction!!,
            0
        )
        
        PacketMineModule.mc.networkHandler?.sendPacket(packet)
        
        // 切换回原来的槽位
        if (shouldSwitchNow) {
            val switchBackPacket = UpdateSelectedSlotC2SPacket(oldSlot)
            PacketMineModule.mc.networkHandler?.sendPacket(switchBackPacket)
        }
        
        return false
    }
    
    private fun findBestToolSlot(state: BlockState): Int? {
        val player = PacketMineModule.mc.player ?: return null
        var bestSlot: Int? = null
        var bestSpeed = 0f
        
        for (i in 0..8) {
            val stack = player.inventory.getStack(i)
            val speed = stack.getMiningSpeedMultiplier(state)
            if (speed > bestSpeed) {
                bestSpeed = speed
                bestSlot = i
            }
        }
        
        return bestSlot
    }
}
