package com.siy.tenseiga.parser.annoparser

import com.siy.tenseiga.entity.InsertFuncInfo
import com.siy.tenseiga.entity.TransformInfo
import com.siy.tenseiga.ext.FILTER_TYPE
import com.siy.tenseiga.ext.value
import com.siy.tenseiga.interfaces.AnnotationParser
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

object InsertFuncParser : AnnotationParser {
    override fun parser(classNode: ClassNode, methodNode: MethodNode, transformInfo: TransformInfo) {
        val annotations = methodNode.visibleAnnotations
        var filter = listOf<String>()
        annotations.forEach {
            when (it.desc) {
                FILTER_TYPE.descriptor -> {
                    filter = (it.value as? ArrayList<*>)?.map { item ->
                        item as String
                    } ?: listOf()
                }
            }
        }

        transformInfo.insertFuncInfo.add(
            InsertFuncInfo(
                methodNode,
                filter
            )
        )
    }
}