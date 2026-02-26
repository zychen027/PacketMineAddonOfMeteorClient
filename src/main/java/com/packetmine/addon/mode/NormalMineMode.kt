package com.packetmine.addon.mode

import com.packetmine.addon.MineTarget
import com.packetmine.addon.PacketMineModule
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair
import meteordevelopment.meteorclient.utils.player.ChatUtils
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.Hand

/**
 * 标准挖掘模式
 * 
 * 流程：
 * 1. 发送 START_DESTROY_BLOCK 包
 * 2. 积累挖掘进度
 * 3. 达到阈值后发送 STOP_DESTROY_BLOCK 包
 */
object NormalMineMode : MineMode("Normal") {
    
    private var clientSideSet = false
    private var waitForConfirm = true
    
    override fun start(mineTarget: MineTarget) {
        // 发送开始挖掘包
        val packet = PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
            mineTarget.targetPos,
            mineTarget.direction!!,
            0
        )
        
        PacketMineModule.mc.networkHandler?.sendPacket(packet)
        
        // 挥动手臂
        PacketMineModule.mc.player?.swingHand(Hand.MAIN_HAND)
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
        
        // 如果启用了客户端设置，在客户端破坏方块
        if (clientSideSet) {
            PacketMineModule.mc.interactionManager?.breakBlock(mineTarget.targetPos)
        }
        
        mineTarget.finished = true
        
        // 如果不需要等待确认，重置目标
        if (!waitForConfirm) {
            PacketMineModule.INSTANCE.resetTarget()
        }
    }
    
    override fun shouldUpdate(mineTarget: MineTarget, slot: IntObjectImmutablePair<ItemStack>?): Boolean {
        return !mineTarget.finished
    }
}
