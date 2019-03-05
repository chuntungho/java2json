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
import static java.util.Objects.nonNull;

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

    private boolean isNormalType(String typeName) {
        return normalTypes.containsKey(typeName);
    }

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
            KV kv = getFields(selectedClass);
            String json = kv.toPrettyJson();
            StringSelection selection = new StringSelection(json);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
            String message = "Convert " + selectedClass.getName() + " to JSON success, copied to clipboard.";
            Notification success = notificationGroup.createNotification(message, NotificationType.INFORMATION);
            Notifications.Bus.notify(success, project);
        } catch (Exception ex) {
            Notification error = notificationGroup.createNotification("Convert to JSON failed.", NotificationType.ERROR);
            Notifications.Bus.notify(error, project);
        }
    }

    public KV getFields(PsiClass psiClass) {
        if (psiClass == null) {
            System.err.println("psi class is null");
            return KV.create();
        }

        KV kv = KV.create();
        KV commentKV = KV.create();

        for (PsiField field : psiClass.getAllFields()) {
            PsiType type = field.getType();
            String name = field.getName();

            if (nonNull(field.getDocComment()) && nonNull(field.getDocComment().getText())) {
                commentKV.set(name, field.getDocComment().getText());
            }

            if (type instanceof PsiPrimitiveType) {
                kv.set(name, getDefaultValueOfType(type));
            } else {
                String fieldTypeName = type.getPresentableText();
                if (isNormalType(fieldTypeName)) {
                    kv.set(name, normalTypes.get(fieldTypeName));
                } else if (type instanceof PsiArrayType) {
                    PsiType deepType = type.getDeepComponentType();
                    ArrayList list = new ArrayList<>();
                    String deepTypeName = deepType.getPresentableText();
                    if (deepType instanceof PsiPrimitiveType) {
                        list.add(getDefaultValueOfType(deepType));
                    } else if (isNormalType(deepTypeName)) {
                        list.add(normalTypes.get(deepTypeName));
                    } else {
                        list.add(getFields(resolveClassInType(deepType)));
                    }
                    kv.set(name, list);
                } else if (fieldTypeName.startsWith("List")) {
                    PsiType iterableType = extractIterableTypeParameter(type, false);
                    PsiClass iterableClass = resolveClassInClassTypeOnly(iterableType);
                    ArrayList list = new ArrayList<>();
                    String classTypeName = iterableClass.getName();
                    if (isNormalType(classTypeName)) {
                        list.add(normalTypes.get(classTypeName));
                    } else {
                        list.add(getFields(iterableClass));
                    }
                    kv.set(name, list);
                } else {
                    System.out.println(name + ":" + type);
                    kv.set(name, getFields(resolveClassInType(type)));
                }
            }
        }

        if (commentKV.size() > 0) {
            kv.set("@comment", commentKV);
        }

        return kv;
    }
}
