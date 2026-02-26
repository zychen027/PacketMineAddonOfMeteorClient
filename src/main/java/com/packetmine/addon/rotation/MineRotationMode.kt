package com.packetmine.addon.rotation

import com.packetmine.addon.MineTarget
import com.packetmine.addon.PacketMineModule

/**
 * 旋转模式枚举
 * 
 * 定义玩家视角如何跟随目标方块
 */
enum class MineRotationMode(
    val name: String
) {
    
    /**
     * 开始挖掘时旋转
     */
    ON_START("OnStart") {
        
        override fun shouldRotate(mineTarget: MineTarget): Boolean {
            return !mineTarget.started
        }
        
        override fun handleFail(mineTarget: MineTarget): Boolean {
            return if (shouldRotate(mineTarget)) {
                // 暂停挖掘
                true
            } else {
                false
            }
        }
    },
    
    /**
     * 挖掘完成时旋转
     */
    ON_STOP("OnStop") {
        
        override fun shouldRotate(mineTarget: MineTarget): Boolean {
            return mineTarget.progress >= PacketMineModule.INSTANCE.breakDamage
        }
        
        override fun handleFail(mineTarget: MineTarget): Boolean {
            return if (shouldRotate(mineTarget)) {
                // 暂停挖掘
                true
            } else {
                false
            }
        }
    },
    
    /**
     * 开始和完成时都旋转
     */
    BOTH("Both") {
        
        override fun shouldRotate(mineTarget: MineTarget): Boolean {
            return ON_START.shouldRotate(mineTarget) || ON_STOP.shouldRotate(mineTarget)
        }
        
        override fun handleFail(mineTarget: MineTarget): Boolean {
            return if (shouldRotate(mineTarget)) {
                // 暂停挖掘
                true
            } else {
                false
            }
        }
    },
    
    /**
     * 始终保持朝向目标
     */
    ALWAYS("Always") {
        
        override fun shouldRotate(mineTarget: MineTarget): Boolean {
            return true
        }
        
        override fun handleFail(mineTarget: MineTarget): Boolean {
            return if (!mineTarget.started) {
                // 暂停挖掘
                true
            } else {
                // 中止挖掘
                mineTarget.abort(force = true)
                PacketMineModule.INSTANCE.resetTarget()
                true
            }
        }
    },
    
    /**
     * 从不旋转
     */
    NEVER("Never") {
        
        override fun shouldRotate(mineTarget: MineTarget): Boolean {
            return false
        }
        
        override fun handleFail(mineTarget: MineTarget): Boolean {
            return false
        }
    };
    
    /**
     * 是否应该旋转
     */
    abstract fun shouldRotate(mineTarget: MineTarget): Boolean
    
    /**
     * 处理旋转失败
     * @return true 表示应该暂停挖掘
     */
    abstract fun handleFail(mineTarget: MineTarget): Boolean
}
