package com.siy.tansaga

import com.android.build.gradle.AppExtension
import com.didiglobal.booster.gradle.GTE_V3_4
import com.didiglobal.booster.gradle.getAndroid
import com.siy.tansaga.ext.TExtension
import com.siy.tansaga.transform.DoKitCommTransform
import com.siy.tansaga.transform.DoKitCommTransformV34
import com.siy.tansaga.transform.TansagaBaseTransform
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * @author  Siy
 */
class TansagaPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        when {
            project.plugins.hasPlugin("com.android.application") || project.plugins.hasPlugin("com.android.dynamic-feature") -> {
              val ex =   project.extensions.create("TExtension", TExtension::class.java, project)
                project.getAndroid<AppExtension>().registerTransform(commNewInstance(project,ex))
            }
        }
    }


    private fun commNewInstance(project: Project,t:TExtension): TansagaBaseTransform = when {
        GTE_V3_4 -> DoKitCommTransformV34(project,t)
        else -> DoKitCommTransform(project,t)
    }
}