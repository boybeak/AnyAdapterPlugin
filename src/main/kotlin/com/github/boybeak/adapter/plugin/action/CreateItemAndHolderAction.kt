package com.github.boybeak.adapter.plugin.action

import com.github.boybeak.adapter.plugin.CreateDialog
import com.intellij.ide.util.DirectoryUtil
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.core.getPackage
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtClass
import java.io.File
import java.util.*

class CreateItemAndHolderAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        val vf = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val directory = vf.toPsiDirectory(project) ?: return
        CreateDialog(
            project,
            directory
        ) { source, layout, item, holder ->
            doCreate(project, directory, source, layout, item, holder)
        }.show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(CommonDataKeys.VIRTUAL_FILE)?.isDirectory ?: false
    }

    private fun doCreate(project: Project, dir: PsiDirectory, source: String, layout: String, item: String, holder: String) {
        val (sourcePkg, sourceName) = with(source) {
            val lastDotIdx = this.lastIndexOf('.')
            Pair(substring(0, lastDotIdx), substring(lastDotIdx + 1))
        }
        val (itemPkg, itemName) = with(item) {
            val lastDotIdx = this.lastIndexOf('.')
            Pair(substring(0, lastDotIdx), substring(lastDotIdx + 1))
        }
        val (holderPkg, holderName) = with(holder) {
            val lastDotIdx = this.lastIndexOf('.')
            Pair(substring(0, lastDotIdx), substring(lastDotIdx + 1))
        }
        val layoutName = layout.substring(0, layout.lastIndexOf(" - "))

        createHolder(project, dir, itemPkg, itemName, holderPkg, holderName)
        createItem(project, dir, sourcePkg, sourceName, itemPkg, itemName, layoutName, holderPkg, holderName)
    }

    private fun createItem(project: Project, directory: PsiDirectory, sourcePkg: String, sourceName: String,
                           itemPkg: String, itemName: String, layoutName: String,
                           holderPkg: String, holderName: String) {

        val sourceClz = JavaPsiFacade.getInstance(project).findClass("$sourcePkg.$sourceName",
            GlobalSearchScope.allScope(project)) ?: return

        val ne = sourceClz.navigationElement
        if (ne !is KtClass) {
            return
        }

        val sb = StringBuilder()

        if (isBasicTypes("$sourcePkg.$sourceName")) {
            sb.append("source() == os")
        } else {
            val publicFields = sourceClz.allFields/*.run {
                val publicFields = ArrayList<PsiField>()
                for (f in this) {
                    if (ne.isData()) {
                        publicFields.add(f)
                    } else {
                        val ml = f.modifierList ?: continue

                        println("-- ${f.name} --")
                        println("hasExplicitModifier.public=${ml.hasExplicitModifier("public")}")
                        println("hasExplicitModifier.protected=${ml.hasExplicitModifier("protected")}")
                        println("hasExplicitModifier.internal=${ml.hasExplicitModifier("internal")}")
                        println("hasExplicitModifier.private=${ml.hasExplicitModifier("private")}")
                        if (ml.hasExplicitModifier("public") || ml.hasExplicitModifier("protected")) {
                            publicFields.add(f)
                        }
                    }
                }
                publicFields
            }*/
            if (publicFields.isEmpty()) {
                sb.append("source() == os")
            } else {
                for ((i, field) in publicFields.withIndex()) {
                    if (i > 0) {
                        sb.append(" && ")
                    }
                    if (i % 3 == 0 && i < publicFields.size - 1) {
                        sb.append('\n')
                    }
                    sb.append("source().${field.name} == os.${field.name}")
                }
            }
        }

        val code = StringBuilder("package $itemPkg\n\n").apply {
            if (itemPkg != sourcePkg) {
                append("import ${sourcePkg}.${sourceName}\n")
            }
            if (itemPkg != holderPkg) {
                append("import ${holderPkg}.${holderName}\n")
            }
            append("import com.github.boybeak.adapter.AbsItem\n")
            append("import com.github.boybeak.adapter.ItemImpl\n")

            append("\nclass $itemName(s: $sourceName) : AbsItem<$sourceName>(s) {\n\n")

            append("\toverride fun layoutId(): Int {\n")
            append("\t\treturn R.layout.$layoutName\n")
            append("\t}\n\n")

            append("\toverride fun holderClass(): Class<$holderName> {\n")
            append("\t\treturn $holderName::class.java\n")
            append("\t}\n\n")

            append("\toverride fun areContentsSame(other: ItemImpl<*>): Boolean {\n")
            append("\t\treturn if (other is $itemName) {\n")
            append("\t\t\tval os = other.source()\n")
            append("\t\t\t$sb\n")
            append("\t\t} else {\n\t\t\tfalse\n\t\t}\n")
            append("\t}\n\n")

            append("}")
        }
        project.executeWriteCommand("New Item", this) {
            val fileName = "${itemName}.kt"
            val psiFile = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, Language.findLanguageByID("kotlin")!!, code.toString(), true, false)
            val targetDir = obtainTargetDir(project, directory, itemPkg)
            DirectoryUtil.mkdirs(targetDir.manager, targetDir.virtualFile.path)
            targetDir.add(psiFile)

            val itemFile = File(File(targetDir.virtualFile.path), fileName)
            val itemVF = LocalFileSystem.getInstance().findFileByIoFile(itemFile)
            OpenFileDescriptor(project, itemVF!!, 0).navigateInEditor(project, true)
        }

    }

    private fun createHolder(project: Project, directory: PsiDirectory, itemPkg: String, itemName: String,
                             holderPkg: String, holderName: String) {
        val codeSB = StringBuilder(
            "package $holderPkg\n\n"
        ).apply {
            append("import android.view.View\n")
            append("import com.github.boybeak.adapter.AbsHolder\n")
            append("import com.github.boybeak.adapter.AnyAdapter\n")
            if (itemPkg != holderPkg) {
                append("import $itemPkg.$itemName\n")
            }
            append("\nclass $holderName(v: View) : AbsHolder<$itemName>(v) {\n\n")
            append("\toverride fun onBind(item: $itemName, position: Int, absAdapter: AnyAdapter) {\n")
            append("\t}\n\n")
            append("}")
        }
        project.executeWriteCommand("New Holder", this) {
            val fileName = "${holderName}.kt"
            val psiFile = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, Language.findLanguageByID("kotlin")!!, codeSB.toString(), true, false)
            val targetDir = obtainTargetDir(project, directory, holderPkg)
            DirectoryUtil.mkdirs(targetDir.manager, targetDir.virtualFile.path)
            targetDir.add(psiFile)

            val holderFile = File(File(targetDir.virtualFile.path), fileName)
            val holderVF = LocalFileSystem.getInstance().findFileByIoFile(holderFile)
            OpenFileDescriptor(project, holderVF!!, 0).navigateInEditor(project, false)
        }
    }

    private fun obtainTargetDir(project: Project, baseDir: PsiDirectory, pkg: String): PsiDirectory {
        val dirPkg = baseDir.getPackage()!!.qualifiedName
        val dirPkgNames = dirPkg.split(".")
        val itemPkgNames = pkg.split(".")
        val createPkgs = LinkedList(itemPkgNames).apply {
            removeAll(dirPkgNames)
        }

        var targetDir = baseDir
        while (createPkgs.isNotEmpty()) {
            val subDirName = createPkgs.removeAt(0)
            val subVF = File(targetDir.virtualFile.path, subDirName)
            if (!subVF.exists()) {
                targetDir.createSubdirectory(subDirName)
            }
            targetDir = LocalFileSystem.getInstance().findFileByIoFile(subVF)?.toPsiDirectory(project)!!
        }
        return targetDir
    }

    private fun isBasicTypes(source: String): Boolean {
        return  Boolean::class.java.name == source || Byte::class.java.name == source || Short::class.java.name == source
                || Int::class.java.name == source || Long::class.java.name == source
                || Float::class.java.name == source || Double::class.java.name == source
                || Char::class.java.name == source || String::class.java.name == source
    }

}