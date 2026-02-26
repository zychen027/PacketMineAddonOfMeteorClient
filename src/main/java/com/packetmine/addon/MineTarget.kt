package com.packetmine.addon

import meteordevelopment.meteorclient.utils.player.Rotations
import meteordevelopment.meteorclient.utils.world.getBlockState
import net.minecraft.block.BlockState
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World

/**
 * 挖掘目标类
 * 跟踪单个方块的挖掘状态
 */
class MineTarget(val targetPos: BlockPos) {
    
    var finished = false
    var progress = 0f
    var started = false
    var direction: Direction? = null
    var blockState: BlockState = getBlockState(targetPos)!!
    
    companion object {
        private fun getBlockState(pos: BlockPos): BlockState? {
            val world = PacketMineModule.mc.world ?: return null
            return world.getBlockState(pos)
        }
    }
    
    fun updateBlockState() {
        blockState = getBlockState(targetPos) ?: return
    }
    
    fun isInvalidOrOutOfRange(): Boolean {
        val state = getBlockState(targetPos) ?: return true
        val module = PacketMineModule.INSTANCE
        val invalid = module.activeMode.isInvalid(this, state)
        val distanceSquared = targetPos.getSquaredDistance(PacketMineModule.mc.player!!.eyePos)
        return invalid || distanceSquared > module.keepRange.get() * module.keepRange.get()
    }
    
    fun abort(force: Boolean = false) {
        val module = PacketMineModule.INSTANCE
        val notPossible = !started || finished || !module.activeMode.canAbort
        if (notPossible || !force && targetPos.getSquaredDistance(PacketMineModule.mc.player!!.eyePos) <= module.keepRange.get() * module.keepRange.get()) {
            return
        }
        
        val dir = if (module.abortAlwaysDown.get()) {
            Direction.DOWN
        } else {
            direction ?: Direction.DOWN
        }
        
        val packet = PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
            targetPos,
            dir
        )
        PacketMineModule.mc.networkHandler!!.sendPacket(packet)
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MineTarget) return false
        return targetPos == other.targetPos
    }
    
    override fun hashCode(): Int {
        return targetPos.hashCode()
    }
    
    fun copy(): MineTarget {
        val newTarget = MineTarget(targetPos)
        newTarget.finished = finished
        newTarget.progress = progress
        newTarget.started = started
        newTarget.direction = direction
        return newTarget
    }
}
