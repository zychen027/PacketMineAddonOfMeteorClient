package com.packetmine.addon

import meteordevelopment.meteorclient.addons.MeteorAddon
import meteordevelopment.meteorclient.systems.modules.Category
import meteordevelopment.meteorclient.systems.modules.Modules
import net.minecraft.item.Items

class PacketMineAddon : MeteorAddon() {

    companion object {
        // 自定义分类，建议给一个图标 ItemStack
        val PACKETMINE_CATEGORY: Category = Category("PacketMine", Items.DIAMOND_PICKAXE.defaultStack)
    }

    // 1. 先注册 Category（这是 Meteor 要求的必须步骤）
    override fun onRegisterCategories() {
        Modules.registerCategory(PACKETMINE_CATEGORY)
    }

    // 2. 再在 onInitialize 里注册模块
    override fun onInitialize() {
        Modules.get().add(PacketMineModule())
    }

    override fun getPackage(): String {
        return "com.packetmine.addon"
    }
}
