package com.packetmine.addon

import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.renderer.ShapeMode
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.player.ChatUtils
import meteordevelopment.meteorclient.utils.player.Rotations
import meteordevelopment.meteorclient.utils.render.color.SettingColor
import meteordevelopment.meteorclient.utils.world.BlockUtils
import meteordevelopment.meteorclient.utils.world.getBlockState
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import com.packetmine.addon.mode.*
import com.packetmine.addon.tool.*
import com.packetmine.addon.rotation.MineRotationMode
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair

/**
 * PacketMine 主模块
 * 移植自 LiquidBounce
 * 
 * 通过发送数据包自动挖掘方块
 */
class PacketMineModule : Module("PacketMine", "Automatically mines blocks using packets", Category.WORLD) {
    
    companion object {
        lateinit var INSTANCE: PacketMineModule
    }
    
    // ========== 设置项 ==========
    private val modeSetting = registerModeSettings()
    private val range by settings("Range", 4.5f, 0f..6f)
    private val wallsRange by settings("WallsRange", 4.5f, 0f..6f)
    val keepRange by settings("KeepRange", 25f, 0f..200f)
    
    private val swingModeSetting: Setting<SwingMode> = registerSwingSettings()
    private val switchModeSetting: Setting<SwitchMode> = registerSwitchSettings()
    
    private val rotationModeSetting: Setting<MineRotationMode> = registerRotationSettings()
    val breakDamage by settings("BreakDamage", 1f, 0f..2f)
    val abortAlwaysDown by settings("AbortAlwaysDown", false)
    
    private val targetColor: Setting<SettingColor> = registerColorSettings()
    
    // ========== 挖掘模式 ==========
    private val modes = listOf(
        NormalMineMode,
        ImmediateMineMode,
        CivMineMode
    )
    
    private var modeIndex = 0
    val activeMode: MineMode
        get() = modes[modeIndex]
    
    // ========== 工具切换模式 ==========
    private val switchModes = listOf(
        AlwaysToolMode,
        PostStartToolMode,
        OnStopToolMode,
        NeverToolMode
    )
    
    private var switchModeIndex = 0
    val activeSwitchMode: MineToolMode
        get() = switchModes[switchModeIndex]
    
    // ========== 状态 ==========
    private var target: MineTarget? = null
    private var lastClickTime = 0L
    private val selectDelay = 200L
    
    init {
        INSTANCE = this
    }
    
    override fun onActivate() {
        target = null
    }
    
    override fun onDeactivate() {
        target?.abort(force = true)
        target = null
    }
    
    // ========== 事件处理 ==========
    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        val currentTarget = target ?: return
        
        // 检查目标是否有效
        if (currentTarget.isInvalidOrOutOfRange()) {
            target = null
            return
        }
        
        currentTarget.updateBlockState()
        handleBreaking(currentTarget)
    }
    
    @EventHandler
    private fun onRender3D(event: Render3DEvent) {
        val currentTarget = target ?: return
        
        // 渲染目标框
        renderTarget(currentTarget)
    }
    
    // ========== 挖掘逻辑 ==========
    private fun handleBreaking(mineTarget: MineTarget) {
        val player = mc.player ?: return
        val world = mc.world ?: return
        
        // 检查是否看向目标
        val hit = mc.crosshairTarget
        val invalidHit = hit == null || 
                        hit.type != HitResult.Type.BLOCK || 
                        (hit as BlockHitResult).blockPos != mineTarget.targetPos
        
        if (invalidHit && rotationModeSetting.get().handleFail(mineTarget)) {
            return
        }
        
        // 确定挖掘方向
        val raytrace = raytraceBlock(
            Math.max(range, wallsRange).toDouble() + 1.0,
            mineTarget.targetPos,
            mineTarget.blockState
        )
        
        mineTarget.direction = raytrace?.side ?: Direction.DOWN
        
        // 创造模式
        if (player.isCreative) {
            val packet = PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                mineTarget.targetPos,
                mineTarget.direction!!,
                0
            )
            mc.networkHandler?.sendPacket(packet)
            swingHand()
            return
        }
        
        // 获取最佳工具
        val slot = activeSwitchMode.getSlot(mineTarget.blockState)
        
        if (!mineTarget.started) {
            startBreaking(slot, mineTarget)
        } else if (activeMode.shouldUpdate(mineTarget, slot)) {
            updateBreakingProgress(mineTarget, slot)
            
            if (mineTarget.progress >= breakDamage && !mineTarget.finished) {
                activeMode.finish(mineTarget)
                activeSwitchMode.getSwitchingMethod().switchBack()
            }
        }
    }
    
    private fun startBreaking(slot: IntObjectImmutablePair<ItemStack>?, mineTarget: MineTarget) {
        activeSwitchMode.switch(slot, mineTarget)
        
        activeMode.start(mineTarget)
        mineTarget.started = true
    }
    
    private fun updateBreakingProgress(mineTarget: MineTarget, slot: IntObjectImmutablePair<ItemStack>?) {
        mineTarget.progress += activeSwitchMode.getBlockBreakingDelta(
            mineTarget.targetPos,
            mineTarget.blockState,
            slot?.second()
        )
        
        activeSwitchMode.switch(slot, mineTarget)
    }
    
    // ========== 辅助方法 ==========
    fun setTarget(pos: BlockPos) {
        if (target?.finished != false && activeMode.canManuallyChange || target == null) {
            target = MineTarget(pos)
        }
    }
    
    fun resetTarget() {
        target = null
    }
    
    private fun raytraceBlock(range: Double, pos: BlockPos, state: BlockState): BlockHitResult? {
        val player = mc.player ?: return null
        val eyePos = player.eyePos
        val rotation = Rotations.serverRotation
        
        // 简化的 raytrace 实现
        return mc.world?.raycast(
            net.minecraft.util.math.Box(pos),
            eyePos,
            eyePos.add(Vec3d.fromPolar(rotation.x, rotation.y).multiply(range)),
            pos,
            net.minecraft.block.ShapeType.OUTLINE,
            net.minecraft.fluid.FluidState::isEmpty
        ) as? BlockHitResult
    }
    
    private fun swingHand() {
        when (swingModeSetting.get()) {
            SwingMode.Client -> mc.player?.swingHand(Hand.MAIN_HAND)
            SwingMode.Packet -> {
                // 发送挥动手臂包
            }
            SwingMode.None -> {}
        }
    }
    
    private fun renderTarget(mineTarget: MineTarget) {
        // 渲染目标方块边框
        val box = net.minecraft.util.math.Box(mineTarget.targetPos)
        val color = targetColor.get()
        
        // 使用 Meteor 的渲染 API
        // renderer.box(box, color, color, ShapeMode.Both, 0)
    }
    
    // ========== 设置注册方法 ==========
    private fun registerModeSettings(): SettingGroup {
        val sg = settings.createGroup("Mode")
        
        sg.add(EnumSetting.Builder<MineModeType>()
            .name("Mode")
            .description("Mining mode")
            .defaultValue(MineModeType.Normal)
            .build()
        )
        
        return sg
    }
    
    private fun registerSwingSettings(): Setting<SwingMode> {
        return EnumSetting.Builder<SwingMode>()
            .name("Swing")
            .description("Swing mode")
            .defaultValue(SwingMode.Client)
            .build()
    }
    
    private fun registerSwitchSettings(): Setting<SwitchMode> {
        return EnumSetting.Builder<SwitchMode>()
            .name("Switch")
            .description("Tool switch mode")
            .defaultValue(SwitchMode.OnStop)
            .build()
    }
    
    private fun registerRotationSettings(): Setting<MineRotationMode> {
        return EnumSetting.Builder<MineRotationMode>()
            .name("Rotation")
            .description("Rotation mode")
            .defaultValue(MineRotationMode.Never)
            .build()
    }
    
    private fun registerColorSettings(): Setting<SettingColor> {
        return ColorSetting.Builder()
            .name("Target-Color")
            .description("Color of the target block")
            .defaultValue(SettingColor(255, 255, 0, 90))
            .build()
    }
    
    // ========== 枚举类型 ==========
    enum class MineModeType {
        Normal, Immediate, Civ
    }
    
    enum class SwingMode {
        Client, Packet, None
    }
    
    enum class SwitchMode {
        Always, PostStart, OnStop, Never
    }
    
    // ========== 扩展函数 ==========
    private fun <T> settings(name: String, defaultValue: T, range: ClosedFloatingPointRange<Float>): Setting<T> {
        @Suppress("UNCHECKED_CAST")
        return DoubleSetting.Builder()
            .name(name)
            .description(name)
            .defaultValue(defaultValue as Double)
            .range(range.start.toDouble(), range.endInclusive.toDouble())
            .build() as Setting<T>
    }
}
