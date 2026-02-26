package com.packetmine.addon

import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.renderer.ShapeMode
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.player.InvUtils
import meteordevelopment.meteorclient.utils.render.color.Color
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.BlockState
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class PacketMineModule : Module(
    PacketMineAddon.PACKETMINE_CATEGORY,
    "PacketMine",
    "PacketMine module ported from LiquidBounce",
    *emptyArray<String>()
) {

    private val sgGeneral = settings.getDefaultGroup()

    private val range = sgGeneral.add(DoubleSetting.Builder()
        .name("range")
        .description("Mining range.")
        .defaultValue(4.5)
        .min(1.0)
        .max(6.0)
        .build()
    )

    private val mineMode = sgGeneral.add(EnumSetting.Builder<MineModeEnum>()
        .name("mode")
        .description("Mining mode.")
        .defaultValue(MineModeEnum.Normal)
        .build()
    )

    private var currentTarget: MineTarget? = null

    override fun onActivate() {
        currentTarget = null
    }

    override fun onDeactivate() {
        currentTarget?.abort()
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        // 处理输入选择
        if (mc.options.attackKey.wasPressed()) {
            handleInput()
        }

        val target = currentTarget ?: return

        // 检查状态
        if (target.isInvalidOrOutOfRange(range.get())) {
            currentTarget = null
            return
        }

        target.updateBlockState()
        if (target.blockState.isAir) {
            currentTarget = null
            return
        }

        // 启动挖掘
        if (!target.started) {
            startBreaking(target)
        }

        // 核心：更新挖掘进度（包含渲染裂纹）
        target.updateProgress()
        
        // 如果挖掘完成，清除目标
        if (target.finished) {
            InvUtils.swapBack()
            currentTarget = null
        }
    }

    @EventHandler
    private fun onRender(event: Render3DEvent) {
        val target = currentTarget ?: return
        
        // 这里只画额外的黄色高亮框，裂纹由 MineTarget 内部调用 WorldRenderer 渲染
        val color = Color(255, 255, 0, 100)
        event.renderer.box(target.targetPos, color, color, ShapeMode.Both, 0)
    }

    private fun handleInput() {
        val hitResult = mc.crosshairTarget
        if (hitResult is BlockHitResult) {
            val pos = hitResult.blockPos
            if (currentTarget?.targetPos == pos) {
                currentTarget?.abort()
                currentTarget = null
            } else {
                currentTarget = MineTarget(pos)
            }
        }
    }

    private fun startBreaking(target: MineTarget) {
        target.direction = Direction.UP

        // 自动切换最佳工具
        val slot = findBestToolSlot(target.blockState)
        if (slot != -1) {
            InvUtils.swap(slot, false)
        }

        // 发送开始挖掘包
        mc.networkHandler?.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                target.targetPos,
                target.direction
            )
        )

        mc.player?.swingHand(Hand.MAIN_HAND)
        target.started = true
    }

    private fun findBestToolSlot(state: BlockState): Int {
        val player = mc.player ?: return -1
        var bestSlot = -1
        var bestSpeed = 0.0f

        for (i in 0..8) {
            val stack = player.inventory.getStack(i)
            if (stack.isEmpty) continue

            val speed = stack.getMiningSpeedMultiplier(state)
            if (speed > bestSpeed) {
                bestSpeed = speed
                bestSlot = i
            }
        }
        return bestSlot
    }

    enum class MineModeEnum {
        Normal, Immediate, Civ
    }
}
