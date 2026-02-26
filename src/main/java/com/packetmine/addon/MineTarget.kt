package com.packetmine.addon

import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.Hand
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

    // 判断是否无效或超出范围
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

    // 核心逻辑：计算真实的挖掘进度
    fun updateProgress() {
        if (finished) return

        val player = mc.player ?: return
        val world = mc.world ?: return

        // 如果还没开始，不计算进度
        if (!started) return

        // 使用 Minecraft 的真实挖掘速度计算
        // 这会自动考虑：工具硬度、效率附魔、急迫药水、水下挖掘等
        // 注意：这里需要传入 targetPos 参数
        val delta = blockState.calcBlockBreakingDelta(player, world, targetPos)
        
        progress += delta

        // 渲染裂纹 (0-9 的阶段)
        // 进度 progress 是 0.0 ~ 1.0，裂纹阶段是 0 ~ 9
        val stage = (progress * 10.0f).toInt()
        
        // 关键：调用 WorldRenderer 显示裂纹
        // 使用玩家自己的 ID，这样看起来就像玩家自己在挖掘
        mc.worldRenderer?.setBlockBreakingInfo(player.id, targetPos, stage)

        if (progress >= 1.0f) {
            finishBreaking()
        }
    }

    private fun finishBreaking() {
        mc.networkHandler?.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                targetPos,
                direction ?: Direction.UP
            )
        )

        mc.player?.swingHand(Hand.MAIN_HAND)
        finished = true
        
        // 清除裂纹显示
        mc.worldRenderer?.setBlockBreakingInfo(mc.player?.id ?: -1, targetPos, -1)
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
        
        // 清除裂纹
        mc.worldRenderer?.setBlockBreakingInfo(mc.player?.id ?: -1, targetPos, -1)
    }
}
