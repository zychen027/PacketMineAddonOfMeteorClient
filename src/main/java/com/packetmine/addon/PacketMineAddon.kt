package com.packetmine.addon

import meteordevelopment.meteorclient.addons.MeteorAddon
import meteordevelopment.meteorclient.systems.modules.Category
import meteordevelopment.meteorclient.systems.modules.Modules

class PacketMineAddon : MeteorAddon() {

    companion object {
        // 正确创建 Category 实例
        val PACKETMINE_CATEGORY: Category = Category("PacketMine")
    }

    override fun onInitialize() {
        Modules.get().add(PacketMineModule())
    }

    override fun getPackage(): String {
        return "com.packetmine.addon"
    }
}
