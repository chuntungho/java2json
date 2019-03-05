package com.linsage;

import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Map;

import static com.intellij.psi.util.PsiTypesUtil.getDefaultValueOfType;
import static com.intellij.psi.util.PsiUtil.*;

public class Java2JsonAction extends AnAction {

    private final NotificationGroup notificationGroup;

    public Java2JsonAction() {
        notificationGroup = new NotificationGroup("Java2Json.NotificationGroup", NotificationDisplayType.BALLOON, true);
    }

    @NonNls
    private final Map<String, Object> normalTypes = Map.of(
            "Boolean", false,
            "Byte", 0,
            "Short", (short) 0,
            "Integer", 0,
            "Long", 0L,
            "Float", 0.0F,
            "Double", 0.0D,
            "String", "",
            "BigDecimal", 0.0,
            "Date", ""
    );

    @Override
    public void actionPerformed(AnActionEvent e) {
        Editor editor = e.getDataContext().getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            throw new RuntimeException("not found editor");
        }

        PsiFile psiFile = e.getDataContext().getData(CommonDataKeys.PSI_FILE);
        Project project = editor.getProject();

        PsiElement referenceAt = psiFile.findElementAt(editor.getCaretModel().getOffset());
        PsiClass selectedClass = (PsiClass) PsiTreeUtil.getContextOfType(referenceAt, new Class[]{PsiClass.class});
        try {

            LinkedKeyValueMemory linkedKeyValueMemory = getFields(selectedClass);

            StringSelection selection = new StringSelection(linkedKeyValueMemory.toPrettyJson());

            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);

            Notification success = notificationGroup.createNotification(
                    "Convert " + selectedClass.getName() + " to JSON success, copied to clipboard.", NotificationType.INFORMATION);
            Notifications.Bus.notify(success, project);
        } catch (Exception ex) {
            Notification error = notificationGroup.createNotification("Convert to JSON failed.", NotificationType.ERROR);
            Notifications.Bus.notify(error, project);
        }
    }

    public LinkedKeyValueMemory getFields(PsiClass psiClass) {
        LinkedKeyValueMemory memory = new LinkedKeyValueMemory();

        if (psiClass == null) {
            System.err.println("psi class is null");
            return memory;
        }

        for (PsiField field : psiClass.getAllFields()) {

            PsiType type = field.getType();
            String name = field.getName();

            if (type instanceof PsiPrimitiveType) {
                memory.set(name, getDefaultValueOfType(type));

                continue;
            }

            String fieldTypeName = type.getPresentableText();
            if (normalTypes.containsKey(fieldTypeName)) {
                memory.set(name, normalTypes.get(fieldTypeName));

                continue;
            }

            if (type instanceof PsiArrayType) {
                PsiType deepType = type.getDeepComponentType();
                java.util.List<Object> list = new ArrayList<>();
                String deepTypeName = deepType.getPresentableText();
                if (deepType instanceof PsiPrimitiveType) {
                    list.add(getDefaultValueOfType(deepType));
                } else if (normalTypes.containsKey(deepTypeName)) {
                    list.add(normalTypes.get(deepTypeName));
                } else {
                    list.add(this.getFields(resolveClassInType(deepType)));
                }
                memory.set(name, list);

                continue;
            }

            if (fieldTypeName.startsWith("List")) {
                PsiType iterableType = extractIterableTypeParameter(type, false);
                PsiClass iterableClass = resolveClassInClassTypeOnly(iterableType);
                ArrayList list = new ArrayList<>();
                String classTypeName = iterableClass.getName();
                if (normalTypes.containsKey(classTypeName)) {
                    list.add(normalTypes.get(classTypeName));
                } else {
                    list.add(getFields(iterableClass));
                }
                memory.set(name, list);

                continue;
            }

        }

        return memory;
    }
}
