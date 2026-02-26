package com.packetmine.addon.tool

import com.packetmine.addon.MineTarget

/**
 * 从不切换工具模式
 * 
 * 特点：
 * - 不会自动切换工具
 * - 使用原版挖掘速度计算
 */
object NeverToolMode : MineToolMode("Never", switchesNever = true) {
    
    override fun shouldSwitch(mineTarget: MineTarget): Boolean {
        return false
    }
}
