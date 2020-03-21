package com.github.boybeak.adapter.plugin.action

import com.github.boybeak.adapter.plugin.NotifyHelper
import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.CodeInsightAction
import com.intellij.codeInsight.hint.HintManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.util.ImportInsertHelperImpl
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

class ViewBindingAction : CodeInsightAction(), CodeInsightActionHandler {

    override fun getHandler() = this

    override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
        val ktFile = psiFile as KtFile

        val itemKtFile = getItemPsiFile(project, ktFile)
        if (itemKtFile == null) {
            NotifyHelper.showError(project, "AnyAdapter error", "The match item file for ${ktFile.name} not found!!")
            return
        }
        val bindingRef = getViewBinding(itemKtFile)
        if (bindingRef == null) {
            NotifyHelper.showError(project, "AnyAdapter error", "The ViewBinding file for ${ktFile.name} not found!!")
            return
        }

        val ktClass = ktFile.declarations.singleOrNull() as? KtClass
        project.executeWriteCommand("new binding", this) {

            if (!ktFile.containsImport(bindingRef)) {
                ImportInsertHelperImpl.addImport(project, ktFile, FqName(bindingRef), false, null)
            }

            if (!ktFile.containsBindingProperty()) {
                val clzName = bindingRef.substring(bindingRef.lastIndexOf('.') + 1)
                val ktFactory = KtPsiFactory(project, false)

                val p = ktFactory.createProperty("private val binding = ${clzName}.bind(v)")

                findBindingProperty(ktClass!!)?.delete()
                ktClass.addDeclarationBefore(p, findOnBindFun(ktClass))

            }
            ""
        }
    }

    override fun isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean {
        return if (file is KtFile) {
            val ktClz = file.declarations.singleOrNull() as? KtClass ?: return false
            ktClz.getSuperTypeList()?.entries.run {
                val isEnable = if (this == null) {
                    false
                } else {
                    var isSubTypeOfAbsHolder = false
                    for (it in this) {
                        isSubTypeOfAbsHolder = it.typeReference?.text?.contains("AbsHolder") ?: false
                        if (isSubTypeOfAbsHolder) {
                            break
                        }
                    }
                    isSubTypeOfAbsHolder
                }
                isEnable
            }
        } else {
            false
        }
    }

    private fun KtFile.containsImport(impt: String): Boolean {
        val imports = importList?.imports ?: return false
        for (i in imports) {
            val path = i.importPath?.pathStr ?: continue
            if (path == impt) {
                return true
            }
        }
        return false
    }
    private fun KtFile.containsBindingProperty(): Boolean {
        for (d in declarations) {
            if (d is KtProperty && d.name == "binding") {
                return true
            }
        }
        return false
    }

    private fun getItemPsiFile(project: Project, holderFile: KtFile): KtFile? {
        val holderKlass = holderFile.declarations.singleOrNull() as? KtClass ?: return null
        var itemKtFile: KtFile? = null
        val holderPkgName = holderFile.packageFqName.asString()

        var itemPkgName = holderPkgName

        for (e in holderKlass.superTypeListEntries) {
            val referencedName = e.typeAsUserType?.referencedName ?: continue
            val typeAsUserText = e.typeAsUserType?.text ?: continue
            if ("AbsHolder" == referencedName) {
                val itemName = typeAsUserText.substring(typeAsUserText.indexOf('<') + 1, typeAsUserText.indexOf('>'))

                val imports = holderFile.importList?.imports
                if (imports != null) {
                    for (impt in imports) {
                        val imptPathStr = impt.importPath?.pathStr ?: continue
                        if (imptPathStr.endsWith(itemName)) {
                            itemPkgName = imptPathStr.replace(".${itemName}", "")
                            break
                        }
                    }
                }

                for (item in FilenameIndex.getFilesByName(project, "${itemName}.kt", GlobalSearchScope.allScope(project))) {
                    if (item !is KtFile) continue
                    val pkgName = item.packageFqName.asString()
                    if (pkgName == itemPkgName) {
                        itemKtFile = item
                        break
                    }
                }
                break
            }
        }
        return itemKtFile
    }

    private fun getViewBinding(itemKtFile: KtFile): String? {
        fun parse(retLine: String): String? {
            val rLayoutPart = retLine.replace(Regex("return\\s+"), "")
            val rLayout = "R.layout."
            var pkg: String? = null
            if (rLayoutPart.startsWith(rLayout)) {
                val impts = itemKtFile.importList?.imports ?: return null
                for (impt in impts) {
                    val path = impt.importPath?.pathStr ?: continue
                    if (path.endsWith(".R")) {
                        pkg = path.replace(".R", "")
                        break
                    }
                }
            } else {
                pkg = rLayoutPart.substring(0, rLayoutPart.indexOf(rLayout))
            }
            if (pkg == null) {
                return null
            }
            val layoutNameSB = StringBuilder(rLayoutPart.substring(rLayoutPart.lastIndexOf('.') + 1))
            if (layoutNameSB[0] in 'a'..'z') {
                layoutNameSB[0] = layoutNameSB[0] - 32
            }
            for (i in 1 until layoutNameSB.length) {
                val c1 = layoutNameSB[i - 1]
                if (c1 != '_') {
                    continue
                }
                val c2 = layoutNameSB[i]
                if (c2 == '_') {
                    continue
                }
                if (c2 in '0'..'9') {
                    continue
                }
                if (c2 in 'a'..'z') {
                    layoutNameSB[i] = c2 - 32
                }
            }
            val bindingName = "${layoutNameSB.toString().replace("_", "")}Binding"

            return "$pkg.databinding.$bindingName"
        }

        val itemKlass = itemKtFile.declarations.singleOrNull() as? KtClass ?: return null
        itemKtFile.importList?.imports
        for (d in itemKlass.declarations) {
            if ("layoutId" == d.name) {
                val ktFun = d as KtNamedFunction
                val bodyExp = ktFun.bodyExpression?.text ?: return null
                val reg = Regex("return\\s+[\\w+.]*R.layout.\\w+")
                var multipleCommentStart = false
                for (line in bodyExp.split('\n')) {
                    if (line.trim().startsWith("//")) {
                        continue
                    }
                    if (line.contains("/*")) {
                        multipleCommentStart = true
                    }
                    if (line.contains("*/")) {
                        multipleCommentStart = false
                    }
                    if (multipleCommentStart) {
                        continue
                    }
                    val result = reg.findAll(bodyExp)
                    if (result.count() == 0) {
                        continue
                    }
                    return parse(result.last().value)
                }

                break
            }
        }
        return null
    }

    private fun findOnBindFun(ktClass: KtClass): KtDeclaration? {
        for (d in ktClass.declarations) {
            if (d is KtNamedFunction && d.name == "onBind") {
                return d
            }
        }
        return null
    }

    private fun findBindingProperty(ktClass: KtClass): KtProperty? {
        for (d in ktClass.getProperties()) {
            if (d.name == "binding") {
                return d
            }
        }
        return null
    }

}