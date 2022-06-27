package com.siy.tansaga.transform

import com.didiglobal.booster.transform.TransformContext
import com.didiglobal.booster.transform.asm.filter
import com.siy.tansaga.entity.ReplaceInfo
import com.siy.tansaga.ext.*
import com.siy.tansaga.parser.OP_CALL
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*


/**
 *
 *替换的基本逻辑就是下面的啦
 *
 *  public void printLog(String str) {
 *       Log.e("siy", str);
 *   }
 *              |
 *              |
 *              |
 *              ↓
 *
 *  public void printLog(String str) {
 *     com_siy_tansaga_HookJava_replaceHook(this, str);
 *  }
 *
 * private void printLog$___backup___(String var1) {
 *     Log.e("siy", var1);
 *  }
 *
 *
 *  private static void com_siy_tansaga_HookJava_hookPrintLog(OrginJava var0, String var1) {
 *     boolean var2 = true;
 *     var0.printLog$___backup___(var1);
 *     Toast.makeText(App.INSTANCE, "replaceHook", 1).show();
 *  }
 *
 *
 *
 *
 *
 * @author  Siy
 * @since  2022/5/31
 */
class ReplaceClassNodeTransform(private val replaceInfos: List<ReplaceInfo>, cnt: ClassNodeTransform?) : ClassNodeTransform(cnt) {

    /**
     * 如果不为空就是需要hook的类
     */
    private var klass: ClassNode? = null

    /**
     * 当前类所对应的ReplaceInfo，一个类可能对应几个ReplaceInfo
     */
    private lateinit var infos: List<ReplaceInfo>

    override fun visitorClassNode(context: TransformContext, klass: ClassNode) {
        super.visitorClassNode(context, klass)

        infos = replaceInfos.filter {
            it.targetClass == klass.name
        }

        if (infos.isNotEmpty()) {
            this.klass = klass
        }
    }

    override fun visitorMethod(context: TransformContext, method: MethodNode) {
        super.visitorMethod(context, method)
        klass?.let { clazz ->
            infos.forEach { info ->
                val sameOwner =
                    clazz.name == info.targetClass || (context.klassPool[info.targetClass].isAssignableFrom(clazz.name))
                val sameName = info.targetMethod == method.name
                val sameDesc = info.targetDesc == method.desc
                if (sameOwner && sameName && sameDesc) {
                    //判断一下hook方法和真实方法是不是都是静态的
                    if (((info.hookMethod.access xor method.access) and Opcodes.ACC_STATIC) != 0) {
                        throw IllegalStateException(
                            info.hookClass + "." + info.hookMethod.name + " should have the same static flag with "
                                    + clazz.name + "." + method.name
                        )
                    }

                    val backupTargetMethod = createBackupForTargetMethod(method)
                    val hookMethod = copyHookMethodAndReplacePlaceholder(info, backupTargetMethod)

                    replaceMethodBody(method) {
                        it.add(
                            MethodInsnNode(
                                TypeUtil.getOpcodeByAccess(hookMethod.access),
                                klass?.name,
                                hookMethod.name,
                                hookMethod.desc
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * 给被hook的方法创建一个拷贝
     *
     * @param targetMethod 需要创建拷贝的方法
     *
     * @return 新生成的拷贝方法
     */
    private fun createBackupForTargetMethod(targetMethod: MethodNode): MethodNode {
        val newAccess: Int = targetMethod.access and (Opcodes.ACC_PROTECTED or Opcodes.ACC_PUBLIC).inv() or Opcodes.ACC_PRIVATE
        val newName = targetMethod.name + "${'$'}___backup___"
        return createMethod(newAccess, newName, targetMethod.desc, targetMethod.exceptions) {
            it.add(targetMethod.instructions)
        }.also {
            klass?.methods?.add(it)
        }
    }

    /**
     *
     *
     * @param info 替换相关信息的数据体
     * @param methodNode 替换Origin方法调用的方法
     *
     * @return 返回新生成的方法
     */
    private fun copyHookMethodAndReplacePlaceholder(info: ReplaceInfo, methodNode: MethodNode): MethodNode {
        //新生成一个方法，把hook方法拷贝过来，方法变成静态方法，替换里面Origin,This占位符
        return createMethod(
            Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC,
            info.hookClass.replace('/', '_').plus("_${info.hookMethod.name}"),
            TypeUtil.descToStatic(info.hookMethod.access, info.hookMethod.desc, info.targetClass),
            info.hookMethod.exceptions
        ) {
            var hookMethod = info.hookMethod
            val newMethodNode = MethodNode(Opcodes.ASM7, hookMethod.access, hookMethod.name, hookMethod.desc, hookMethod.signature, hookMethod.exceptions.toTypedArray())
            val mv = AddLocalVarAdapter(Opcodes.ASM7, newMethodNode, hookMethod.access, hookMethod.name, hookMethod.desc)
            hookMethod.accept(mv)
            hookMethod = newMethodNode


            val insns = hookMethod.instructions

            val callInsns = insns.filter { insn ->
                insn.opcode == OP_CALL
            }

            callInsns.forEach { opcall ->
                val ns = loadArgsAndInvoke(methodNode, mv.slotIndex)
                insns.insertBefore(opcall, ns)
                insns.remove(opcall)
            }

            it.add(insns)
        }.also {
            klass?.methods?.add(it)
        }
    }

    /**
     * 加载方法参数并且调用方法
     *
     * @param methodNode 需要调用的方法
     *
     * @param slotIndex
     *
     * @return 返回加载参数和方法调用的指令集
     */
    private fun loadArgsAndInvoke(methodNode: MethodNode, slotIndex: Int): InsnList {
        val insns = InsnList()

        insns.add(VarInsnNode(Opcodes.ASTORE, slotIndex))

        if (!TypeUtil.isStatic(methodNode.access)) {
            insns.add(VarInsnNode(Opcodes.ALOAD, 0))
        }

        //加载方法传入参数
        val params = Type.getArgumentTypes(methodNode.desc)
        params.forEachIndexed { index, type ->
            insns.add(VarInsnNode(Opcodes.ALOAD, slotIndex))
            when (index) {
                0 -> insns.add(InsnNode(Opcodes.ICONST_0))
                1 -> insns.add(InsnNode(Opcodes.ICONST_1))
                2 -> insns.add(InsnNode(Opcodes.ICONST_2))
                3 -> insns.add(InsnNode(Opcodes.ICONST_3))
                4 -> insns.add(InsnNode(Opcodes.ICONST_4))
                5 -> insns.add(InsnNode(Opcodes.ICONST_5))
                in 6..127 -> insns.add(IntInsnNode(Opcodes.BIPUSH, index))
                in 128..255 -> insns.add(IntInsnNode(Opcodes.SIPUSH, index))
            }
            insns.add(InsnNode(Opcodes.AALOAD))
            if (PrimitiveUtil.isPrimitive(type.descriptor)) {
                //如果是基本类型，就要拆箱成基本变量
                val owner = PrimitiveUtil.box(type.descriptor)
                insns.add(TypeInsnNode(Opcodes.CHECKCAST, PrimitiveUtil.virtualType(owner)))
                insns.add(
                    MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        PrimitiveUtil.virtualType(owner),
                        PrimitiveUtil.unboxMethod(owner),
                        "()${type.descriptor}",
                        false
                    )
                )
            } else {
                //如果不是基本数据类型，是引用类型
                insns.add(TypeInsnNode(Opcodes.CHECKCAST, type.internalName))
            }
        }


        insns.add(MethodInsnNode(TypeUtil.getOpcodeByAccess(methodNode.access), klass?.name, methodNode.name, methodNode.desc))
        return insns
    }
}