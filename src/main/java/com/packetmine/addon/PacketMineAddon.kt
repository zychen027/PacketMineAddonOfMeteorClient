package com.packetmine.addon

import meteordevelopment.meteorclient.addons.MeteorAddon
import meteordevelopment.meteorclient.systems.modules.Modules

/**
 * PacketMine Meteor Client 插件入口
 * 移植自 LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * 
 * Copyright (c) 2015 - 2025 CCBlueX
 * Licensed under GPL-3.0
 */
class PacketMineAddon : MeteorAddon() {
    
    override fun onRegister() {
        // 注册模块
        Modules.get().add(PacketMineModule())
    }
    
    override fun onInitialize() {
        // 初始化配置
    }
    
    override fun getPackage(): String {
        return "com.packetmine.addon"
    }
}
