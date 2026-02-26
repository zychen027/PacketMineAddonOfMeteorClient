package com.packetmine.addon.mode

import com.packetmine.addon.MineTarget
import com.packetmine.addon.PacketMineModule
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.Hand

/**
 * 即时挖掘模式
 * 
 * 特点：
 * - 立即发送 STOP_DESTROY_BLOCK 包
 * - 模拟瞬间挖掘
 */
object ImmediateMineMode : MineMode(
    name = "Immediate",
    canManuallyChange = false,
    canAbort = false
) {
    
    private var waitForConfirm = true
    
    override fun start(mineTarget: MineTarget) {
        // 先调用标准模式的开始
        NormalMineMode.start(mineTarget)
        
        // 立即发送停止挖掘包
        val packet = PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
            mineTarget.targetPos,
            mineTarget.direction!!,
            0
        )
        
        PacketMineModule.mc.networkHandler?.sendPacket(packet)
        
        // 挥动手臂
        PacketMineModule.mc.player?.swingHand(Hand.MAIN_HAND)
    }
    
    override fun finish(mineTarget: MineTarget) {
        if (!waitForConfirm) {
            mineTarget.finished = true
            PacketMineModule.INSTANCE.resetTarget()
        }
    }
    
    override fun shouldUpdate(mineTarget: MineTarget, slot: IntObjectImmutablePair<ItemStack>?): Boolean {
        return mineTarget.progress < PacketMineModule.INSTANCE.breakDamage
    }
}
