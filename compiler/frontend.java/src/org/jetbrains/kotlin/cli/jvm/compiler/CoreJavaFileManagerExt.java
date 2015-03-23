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

package org.jetbrains.kotlin.cli.jvm.compiler;

import com.intellij.core.CoreJavaFileManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import kotlin.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.name.ClassId;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.name.Name;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CoreJavaFileManagerExt extends CoreJavaFileManager implements JavaFileManager {
    private static final Logger LOG = Logger.getInstance("#com.intellij.core.CoreJavaFileManager");

    private final List<VirtualFile> myClasspath = new ArrayList<VirtualFile>();

    private PackagesCache packagesCache;
    private final PsiManager myPsiManager;

    public CoreJavaFileManagerExt(PackagesCache packagesCache, PsiManager psiManager) {
        super(psiManager);
        this.packagesCache = packagesCache;
        this.myPsiManager = psiManager;
    }

    public void setPackagesCache(PackagesCache packagesCache) {
        this.packagesCache = packagesCache;
    }

    @Nullable
    public PsiClass findClass(@NotNull ClassId classId, @NotNull final GlobalSearchScope scope) {
        List<Name> packageNameSegments = classId.getPackageFqName().pathSegments();

        final String classNameWithInnerClasses = classId.getRelativeClassName().asString();
        return packagesCache.searchPackages(classId.getPackageFqName(), new Function1<VirtualFile, PsiClass>() {
            @Override
            public PsiClass invoke(VirtualFile dir) {
                return findClassGivenPackage(scope, dir, classNameWithInnerClasses);
            }
        });
    }

    @Override
    public PsiClass findClass(@NotNull String qName, @NotNull GlobalSearchScope scope) {
        //TODO: comment
        ClassId classIdAsTopLevelClass = ClassId.topLevel(new FqName(qName));
        PsiClass psiClass = findClass(classIdAsTopLevelClass, scope);
        if (psiClass != null) {
            return psiClass;
        }
        return super.findClass(qName, scope);
    }

    @NotNull
    @Override
    public PsiClass[] findClasses(@NotNull String qName, @NotNull final GlobalSearchScope scope) {
        final List<PsiClass> result = new ArrayList<PsiClass>();
        ClassId classIdAsTopLevelClass = ClassId.topLevel(new FqName(qName));
        final String classNameWithInnerClasses = classIdAsTopLevelClass.getRelativeClassName().asString();
        packagesCache.searchPackages(classIdAsTopLevelClass.getPackageFqName(), new Function1<VirtualFile, Object>() {
            @Override
            public Object invoke(VirtualFile dir) {
                PsiClass psiClass = findClassGivenPackage(scope, dir, classNameWithInnerClasses);
                if (psiClass != null) {
                    result.add(psiClass);
                }
                return null;
            }
        });
        if (result.isEmpty()) {
            return super.findClasses(qName, scope);
        }
        return result.toArray(new PsiClass[result.size()]);
    }

    @Nullable
    private PsiClass findClassGivenPackage(
            @NotNull GlobalSearchScope scope,
            @NotNull VirtualFile packageDir,
            @NotNull String classNameWithInnerClasses
    ) {
        String topLevelClassName = substringBeforeFirstDot(classNameWithInnerClasses);

        VirtualFile vFile = packageDir.findChild(topLevelClassName + ".class");
        if (vFile == null) vFile = packageDir.findChild(topLevelClassName + ".java");

        if (vFile == null) {
            return null;
        }
        if (!vFile.isValid()) {
            LOG.error("Invalid child of valid parent: " + vFile.getPath() + "; " + packageDir.isValid() + " path=" + packageDir.getPath());
            return null;
        }
        if (!scope.contains(vFile)) {
            return null;
        }

        PsiFile file = myPsiManager.findFile(vFile);
        if (!(file instanceof PsiClassOwner)) {
            return null;
        }

        return findClassInPsiFile(classNameWithInnerClasses, (PsiClassOwner) file);
    }

    @NotNull
    private static String substringBeforeFirstDot(@NotNull String classNameWithInnerClasses) {
        int dot = classNameWithInnerClasses.indexOf('.');
        if (dot < 0) {
            return classNameWithInnerClasses;
        }
        else {
            return classNameWithInnerClasses.substring(0, dot);
        }
    }

    @Nullable
    private static PsiClass findClassInPsiFile(@NotNull String classNameWithInnerClassesDotSeparated, @NotNull PsiClassOwner file) {
        for (PsiClass topLevelClass : file.getClasses()) {
            PsiClass candidate = findClassByTopLevelClass(classNameWithInnerClassesDotSeparated, topLevelClass);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private static PsiClass findClassByTopLevelClass(@NotNull String className, @NotNull PsiClass topLevelClass) {
        if (className.indexOf('.') < 0) {
            return className.equals(topLevelClass.getName()) ? topLevelClass : null;
        }

        Iterator<String> segments = StringUtil.split(className, ".").iterator();
        if (!segments.hasNext() || !segments.next().equals(topLevelClass.getName())) {
            return null;
        }
        PsiClass curClass = topLevelClass;
        while (segments.hasNext()) {
            String innerClassName = segments.next();
            PsiClass innerClass = curClass.findInnerClassByName(innerClassName, false);
            if (innerClass == null) {
                return null;
            }
            curClass = innerClass;
        }
        return curClass;
    }


    @Override
    public void addToClasspath(VirtualFile root) {
        super.addToClasspath(root);
        myClasspath.add(root);
    }
}
