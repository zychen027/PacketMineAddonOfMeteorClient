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
    "Ported from LiquidBounce",          
    *emptyArray<String>()                
) {

    private val sgGeneral = settings.getDefaultGroup()

    // 修复：使用 DoubleSetting 替代 FloatSetting，并显式指定泛型类型
    private val range: Setting<Double> = sgGeneral.add(DoubleSetting.Builder()
        .name("range")
        .description("Mining range.")
        .defaultValue(4.5)
        .min(1.0)
        .max(6.0)
        .build()
    )

    private val breakDamage: Setting<Double> = sgGeneral.add(DoubleSetting.Builder()
        .name("break-damage")
        .description("Damage required to break.")
        .defaultValue(1.0)
        .min(0.0)
        .max(2.0)
        .build()
    )

    private val mineMode: Setting<MineModeEnum> = sgGeneral.add(EnumSetting.Builder<MineModeEnum>()
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
        if (mc.options.attackKey.wasPressed()) {
            handleInput()
        }

        val target = currentTarget ?: return
        handleMining(target)
    }

    @EventHandler
    private fun onRender(event: Render3DEvent) {
        val target = currentTarget ?: return
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

    private fun handleMining(target: MineTarget) {
        // 注意：range.get() 现在返回 Double
        if (target.isInvalidOrOutOfRange(range.get())) {
            currentTarget = null
            return
        }

        target.updateBlockState()
        if (target.blockState.isAir) {
            currentTarget = null
            return
        }

        if (!target.started) {
            startBreaking(target)
        } else {
            updateProgress(target)
        }
    }

    private fun startBreaking(target: MineTarget) {
        target.direction = Direction.UP

        val slot = findBestToolSlot(target.blockState)
        if (slot != -1) {
            InvUtils.swap(slot, false)
        }

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

    private fun updateProgress(target: MineTarget) {
        if (target.finished) return

        target.progress += 0.1f

        // breakDamage.get() 现在返回 Double，需要转换为 Float 或直接比较
        if (target.progress >= breakDamage.get().toFloat()) {
            finishBreaking(target)
        }
    }

    private fun finishBreaking(target: MineTarget) {
        mc.networkHandler?.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                target.targetPos,
                target.direction ?: Direction.UP
            )
        )

        mc.player?.swingHand(Hand.MAIN_HAND)
        target.finished = true

        InvUtils.swapBack()
        currentTarget = null
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
