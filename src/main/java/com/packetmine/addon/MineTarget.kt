package com.packetmine.addon

import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class MineTarget(val targetPos: BlockPos) {
    val mc = MinecraftClient.getInstance()

    var finished = false
    var progress = 0f
    var started = false
    var direction: Direction? = null
    var blockState: BlockState = mc.world?.getBlockState(targetPos)!!

    fun updateBlockState() {
        mc.world?.getBlockState(targetPos)?.let { blockState = it }
    }

    // 修改参数类型为 Double 以匹配 Setting
    fun isInvalidOrOutOfRange(maxRange: Double): Boolean {
        val state = mc.world?.getBlockState(targetPos) ?: return true
        if (state.isAir) return true

        val player = mc.player ?: return true
        val distSq = player.squaredDistanceTo(
            targetPos.x.toDouble(),
            targetPos.y.toDouble(),
            targetPos.z.toDouble()
        )
        return distSq > maxRange * maxRange
    }

    fun abort() {
        if (!started || finished) return

        val networkHandler = mc.networkHandler ?: return
        val dir = direction ?: Direction.DOWN

        networkHandler.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                targetPos,
                dir
            )
        )
    }
}
