package com.packetmine.addon.tool

import com.packetmine.addon.MineTarget
import com.packetmine.addon.PacketMineModule
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair
import meteordevelopment.meteorclient.utils.player.ChatUtils
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket

/**
 * 切换方式枚举
 * 
 * 定义不同的工具切换方法
 */
enum class SwitchMethod(
    val name: String,
    val shouldSync: Boolean
) {
    
    /**
     * 正常切换方式
     * - 切换到工具
     * - 执行挖掘
     * - 自动切换回原槽位
     */
    NORMAL("Normal", true) {
        
        override fun switch(slot: IntObjectImmutablePair<ItemStack>, mineTarget: MineTarget) {
            val player = PacketMineModule.mc.player ?: return
            val desiredSlot = slot.firstInt()
            
            // 发送切换槽位包
            val packet = UpdateSelectedSlotC2SPacket(desiredSlot)
            PacketMineModule.mc.networkHandler?.sendPacket(packet)
            
            // 更新客户端槽位
            player.inventory.selectedSlot = desiredSlot
        }
        
        override fun switchBack() {
            // 由 Meteor 的 SilentHotbar 处理
        }
    },
    
    /**
     * 交换切换方式
     * - 将工具从目标槽位交换到当前槽位
     * - 执行挖掘
     * - 交换回原位置
     */
    SWAP("Swap", false) {
        
        private var exchangedSlot: Int? = null
        
        override fun switch(slot: IntObjectImmutablePair<ItemStack>, mineTarget: MineTarget) {
            val player = PacketMineModule.mc.player ?: return
            val selectedSlot = player.inventory.selectedSlot
            val desiredSlot = slot.firstInt()
            
            if (selectedSlot == desiredSlot) return
            
            exchangedSlot = desiredSlot
            
            // 执行背包交换
            // 这里需要使用 Meteor 的 InventoryAction API
            // 简化实现：
            val tempStack = player.inventory.getStack(desiredSlot)
            player.inventory.setStack(desiredSlot, player.inventory.getStack(selectedSlot))
            player.inventory.setStack(selectedSlot, tempStack)
        }
        
        override fun switchBack() {
            val desiredSlot = exchangedSlot ?: return
            val player = PacketMineModule.mc.player ?: return
            val selectedSlot = player.inventory.selectedSlot
            
            exchangedSlot = null
            
            // 交换回来
            val tempStack = player.inventory.getStack(desiredSlot)
            player.inventory.setStack(desiredSlot, player.inventory.getStack(selectedSlot))
            player.inventory.setStack(selectedSlot, tempStack)
        }
    };
    
    /**
     * 切换到指定槽位
     */
    abstract fun switch(slot: IntObjectImmutablePair<ItemStack>, mineTarget: MineTarget)
    
    /**
     * 切换回原槽位
     */
    abstract fun switchBack()
    
    /**
     * 重置状态
     */
    fun reset() {
        // 重置任何临时状态
    }
}
