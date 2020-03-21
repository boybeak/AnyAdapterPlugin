package com.github.boybeak.adapter.plugin

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import com.intellij.refactoring.ui.ClassNameReferenceEditor
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.layout.*
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHint
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import java.awt.Color
import java.awt.Font
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.io.File
import javax.swing.*


class CreateDialog(
    private val project: Project,
    private val dir: PsiDirectory,
    callback: (source: String, layout: String, item: String, holder: String) -> Unit
) : DialogWrapper(project, true) {

    private val layoutNames = findLayoutFiles()
    private val sourceEditor = ClassNameReferenceEditor(project, null)
    private val layoutTextField = TextFieldWithAutoCompletion.create(project, layoutNames, true, "")
    private val itemTextField = JTextField("")
    private val itemErrorLabel = JLabel("").apply {
        foreground = Color.ORANGE
        font = Font(font.name, font.style, 10)
    }

    private val holderTextField = JTextField("")
    private val holderErrorLabel = JLabel("").apply {
        foreground = Color.ORANGE
        font = Font(font.name, font.style, 10)
    }

    private val pkg = JavaDirectoryService.getInstance().getPackage(dir)!!

    init {
        this.init()
        title = "Test DialogWrapper"
        this.setResizable(false)
        getButton(okAction)?.apply {
            isEnabled = false
            addActionListener {
                callback.invoke(sourceEditor.text, layoutTextField.text,
                    "${pkg.qualifiedName}.${itemTextField.text}", "${pkg.qualifiedName}.${holderTextField.text}")
            }
        }

    }

    override fun createCenterPanel(): JComponent? {
        var isItemEdited = false
        var isHolderEdited = false

        return panel {
            row("Source") {
                sourceEditor().apply {
                    this.component.addDocumentListener(object : DocumentListener {
                        override fun documentChanged(event: DocumentEvent) {
                            val t = event.document.text
                            if (t.isEmpty()) {
                                isItemEdited = false
                                isHolderEdited = false
                            }
                            val clzName = if (t.contains('.')) {
                                t.substring(t.lastIndexOf('.') + 1)
                            } else {
                                t
                            }
                            if (!isItemEdited) {
                                itemTextField.text = if (clzName.isEmpty()) "" else "item.${clzName}Item"
                            }
                            if (!isHolderEdited) {
                                holderTextField.text = if (clzName.isEmpty()) "" else "holder.${clzName}Holder"
                            }
                            refreshOkBtnEnable()
                        }
                    })
                }.focused()
            }
            row("Layout") {
                layoutTextField(CCFlags.growX).apply {
                    this.component.apply {
                        addDocumentListener(object : DocumentListener{
                            override fun documentChanged(event: DocumentEvent) {
                                refreshOkBtnEnable()
                            }
                        })
                    }
                }
            }
            row("Item") {
                label("${pkg.qualifiedName}.")
                itemTextField().apply {
                    this.component.apply {
                        var isFocused = false
                        document.addDocumentListener(object : javax.swing.event.DocumentListener {
                            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {

                            }

                            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                                if (isFocused) {
                                    isItemEdited = true
                                }

                                refreshItemErrorTip()
                                refreshOkBtnEnable()
                            }

                            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                                if (isFocused) {
                                    isItemEdited = true
                                }

                                refreshItemErrorTip()
                                refreshOkBtnEnable()
                            }
                        })
                        addFocusListener(object : FocusListener{
                            override fun focusLost(e: FocusEvent?) {
                                isFocused = false
                            }

                            override fun focusGained(e: FocusEvent?) {
                                isFocused = true
                            }
                        })
                    }
                }
            }
            row(" ") {
                itemErrorLabel()
            }
            row("Holder") {
                label("${pkg.qualifiedName}.")
                holderTextField().apply {
                    this.component.apply {
                        var isFocused = false
                        document.addDocumentListener(object : javax.swing.event.DocumentListener {
                            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                            }

                            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                                if (isFocused) {
                                    isHolderEdited = true
                                }
                                refreshOkBtnEnable()
                                refreshHolderErrorTip()
                            }

                            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                                if (isFocused) {
                                    isHolderEdited = true
                                }
                                refreshOkBtnEnable()
                                refreshHolderErrorTip()
                            }
                        })
                        addFocusListener(object : FocusListener{
                            override fun focusLost(e: FocusEvent?) {
                                isFocused = false
                            }

                            override fun focusGained(e: FocusEvent?) {
                                isFocused = true
                            }
                        })
                    }
                }
            }
            row(" ") {
                holderErrorLabel()
            }
        }
    }

    private fun findLayoutFiles(): List<String> {
        val modules = project.allModules()
        val layoutNames = ArrayList<String>()
        for (i in 1 until modules.size) {
            val module = modules[i]
            val path = module.moduleFilePath.replace("${module.name}.iml", "")
            val resFile = File(path, "src${File.separator}main${File.separator}res")
            val resFiles = resFile.listFiles() ?: continue
            for (dir in resFiles) {
                if (!dir.name.contains("layout")) {
                    continue
                }
                val layouts = dir.listFiles() ?: continue
                for (layout in layouts) {
                    val name = "${layout.nameWithoutExtension} - [${module.name}]"
                    if (layoutNames.contains(name)) {
                        continue
                    }
                    layoutNames.add(name)
                }
            }
        }
        return layoutNames
    }

    private fun refreshOkBtnEnable() {
        getButton(okAction)?.isEnabled = sourceEditor.text.isNotEmpty() && layoutTextField.document.text.isNotEmpty()
                && itemTextField.text.isNotEmpty() && isValidPackage(itemTextField.text) && !isItemExists()
                && holderTextField.text.isNotEmpty() && isValidPackage(holderTextField.text) && !isHolderExists()
    }

    private fun isItemExists(): Boolean {
        return File(dir.virtualFile.path, "${itemTextField.text.replace('.', File.separatorChar)}.kt").exists()
    }

    private fun isHolderExists(): Boolean {
        return File(dir.virtualFile.path, "${holderTextField.text.replace('.', File.separatorChar)}.kt").exists()
    }

    private fun isValidPackage(text: String): Boolean {
        return Regex("(\\w+.)*\\w+").matches(text)
    }

    private fun refreshItemErrorTip() {
        val item = itemTextField.text
        itemErrorLabel.text = if (!isValidPackage(item)) {
            "Not valid package or name."
        } else if (isItemExists()) {
            "The item file already exists."
        } else {
            ""
        }
    }

    private fun refreshHolderErrorTip() {
        val holder = holderTextField.text
        holderTextField.text = if (!isValidPackage(holder)) {
            "Not valid package or name."
        } else if (isHolderExists()) {
            "The holder file already exists."
        } else {
            ""
        }
    }

}