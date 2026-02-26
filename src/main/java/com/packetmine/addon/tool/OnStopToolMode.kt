package com.packetmine.addon.tool

import com.packetmine.addon.MineTarget
import com.packetmine.addon.PacketMineModule

/**
 * 完成时切换工具模式
 * 
 * 特点：
 * - 在挖掘进度达到 breakDamage 时切换工具
 * - 可以选择不同的切换方法
 */
object OnStopToolMode : MineToolMode("OnStop") {
    
    private var switchMethod = SwitchMethod.Normal
    
    override fun shouldSwitch(mineTarget: MineTarget): Boolean {
        return mineTarget.progress >= PacketMineModule.INSTANCE.breakDamage
    }
    
    override fun getSwitchingMethod(): SwitchMethod {
        return switchMethod
    }
}
