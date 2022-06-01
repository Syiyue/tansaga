package com.siy.tansaga

import com.android.aaptcompiler.AaptResourceType
import com.siy.tansaga.base.Origin
import com.siy.tansaga.ext.PrimitiveUtil
import com.siy.tansaga.ext.TypeUtil
import com.siy.tansaga.interfaces.NodeReplacer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode


const val OP_CALL = Int.MAX_VALUE - 5555

const val JAVA_LANG_OBJECT = "java/lang/Object"

private const val VOID = 1

private const val REFERENCE = 2

private const val PRIMITIVE = 3


class AopMethodAdjuster constructor(private val sourcesClass: String, private val methodNode: MethodNode) {

    private val CALL_REPLACER = CallReplacer(methodNode)

    init {
        val desc = methodNode.desc
        var size = Type.getArgumentsAndReturnSizes(desc)
        if (TypeUtil.isStatic(methodNode.access)) {
            size -= 4
        }
        size = (size and 3).coerceAtLeast(size shr 2)
        methodNode.maxStack = size.coerceAtLeast(methodNode.maxStack)
    }

    fun adjust() {
        var insn = methodNode.instructions.first
        while (insn != null) {
            if (insn is MethodInsnNode) {
                insn = transform(insn)
            }
            insn = insn.next
        }
    }

    private fun transform(node: MethodInsnNode): AbstractInsnNode {
        val owner = node.owner
        val name = node.name

        var replacer: NodeReplacer = object : NodeReplacer {
            override fun replace(node: MethodInsnNode) = node
        }

        if (owner == Origin.CLASS_NAME) {
            if (name.startsWith("call")) {
                replacer = CALL_REPLACER
            }
        }
        return replacer.replace(node)
    }

}


private class CallReplacer constructor(private val methodNode: MethodNode) : NodeReplacer {

    private var retType: Int = 0

    private var returnDesc: String? = null

    init {
        val desc = methodNode.desc
        //返回类型的描述
        var retDesc = desc.substring(desc.lastIndexOf(")") + 1)
        if (retDesc == "V") {//void
            retType = VOID
        } else if (retDesc.endsWith(";") || retDesc[0] == '[') {//object or array
            retType = REFERENCE

            if (retDesc[0] != '[' && retDesc.endsWith(";")) {//convert to internal
                retDesc = retDesc.substring(1, retDesc.length - 1)
            }
            returnDesc = retDesc
        } else {//primitive
            retType = PRIMITIVE
            returnDesc = PrimitiveUtil.box(retDesc)
        }
    }


    override fun replace(node: MethodInsnNode): AbstractInsnNode {
        checkReturnType(node)
        node.opcode = OP_CALL
        if (retType != VOID && returnDesc != JAVA_LANG_OBJECT) {
            checkCast(node.next)
//            INVOKESTATIC me/ele/lancet/base/Origin.call ()Ljava/lang/Object;
//            CHECKCAST com/sample/playground/Cup   把这个指令移除了
//            ARETURN
            methodNode.instructions.remove(node.next)
        }

        if (retType == PRIMITIVE) {
            checkUnbox(node.next)
            methodNode.instructions.remove(node.next)
        }
        return node
    }

    private fun checkReturnType(node: MethodInsnNode) {
        val hasRet = !node.name.startsWith("callVoid")//占位符号有返回值类型
        val hasRetType = retType != VOID //替换的方法没有返回值类型
        if (hasRet != hasRetType) {
            illegalState("Called function " + node.owner + "." + node.name + "is illegal.")
        }
    }

    private fun checkCast(insnNode: AbstractInsnNode) {
        if (insnNode !is TypeInsnNode) {
            illegalState("Returned Object type should be cast to origin type immediately.")
        }

        val typeInsnNode = insnNode as TypeInsnNode
        if (typeInsnNode.opcode != Opcodes.CHECKCAST) {
            illegalState("Returned Object type should be cast to origin type immediately.")
        }
        if (typeInsnNode.desc == returnDesc) {
            illegalState("Casted type is expected to be " + returnDesc + " , but is " + typeInsnNode.desc)
        }
    }

    private fun checkUnbox(insnNode: AbstractInsnNode) {
        if (insnNode !is MethodInsnNode) {
            illegalState("Please don't unbox by your self.")
        }
        val methodInsnNode = insnNode as MethodInsnNode
        if (methodInsnNode.owner != returnDesc) {
            illegalState("Please don't unbox by your self.")
        }
        if (methodInsnNode.name != PrimitiveUtil.unboxMethod(returnDesc)) {
            illegalState("Please don't unbox by your self.")
        }
    }

}

private fun illegalState(msg: String) {
    throw IllegalStateException(msg)
}

