/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.lang.resolve.android

import java.util.ArrayList
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.io.ByteArrayInputStream
import kotlin.properties.Delegates
import com.intellij.psi.impl.*
import com.intellij.openapi.components.*
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider.Result

public class CliAndroidUIXmlProcessor(
        project: Project,
        private val manifestPath: String,
        private val mainResDirectory: String
) : AndroidUIXmlProcessor(project) {

    override val resourceManager: CliAndroidResourceManager by Delegates.lazy {
        CliAndroidResourceManager(project, manifestPath, mainResDirectory)
    }

    override val cachedSources: CachedValue<List<String>> by Delegates.lazy {
        cachedValue {
            Result.create(parse(), ModificationTracker.NEVER_CHANGED)
        }
    }

    override fun parseSingleFile(file: PsiFile): List<AndroidWidget> {
        val widgets = arrayListOf<AndroidWidget>()
        val handler = AndroidXmlHandler { id, clazz -> widgets.add(AndroidWidget(id, clazz)) }

        try {
            val inputStream = ByteArrayInputStream(file.getVirtualFile().contentsToByteArray())
            resourceManager.saxParser.parse(inputStream, handler)
            return widgets
        }
        catch (e: Throwable) {
            LOG.error(e)
            return listOf()
        }
    }
}

