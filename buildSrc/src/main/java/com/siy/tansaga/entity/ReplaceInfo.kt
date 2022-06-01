package com.siy.tansaga.entity

import com.siy.tansaga.ext.AsmUtil
import org.objectweb.asm.tree.MethodNode


/**
 *
 * classJar语法
 *
 * @author  Siy
 * @since  2022/5/26
 */
data class ReplaceInfo(
    /**
     * 被替换的类
     */
    val targetClass: String,

    /**
     * 被替换的方法名
     */
    val targetMethod: String,

    /**
     * 替换的类
     */
    val hookClass: String,
    /**
     * 替换的方法
     */
    val hookMethod: MethodNode,

) {

    /**
     * 替换目标方法的desc
     */
    val targetDesc: String
        get() {
            return hookMethod.desc
        }

    private val local = object : ThreadLocal<MethodNode>() {
        @Synchronized
        override fun initialValue(): MethodNode {
            return AsmUtil.clone(hookMethod)
        }
    }

    fun threadLocalNode(): MethodNode? {
        return local.get()
    }


    override fun toString(): String {
        return "ReplaceInfo{ targetClass=$targetClass, " +
                "targetMethod=$targetMethod, " +
                "hookClass=$hookClass, " +
                "hookMethod=${hookMethod.name}, " +
                "targetDesc=$targetDesc}"
    }
}