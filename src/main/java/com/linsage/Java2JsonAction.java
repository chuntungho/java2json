package com.linsage;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.http.client.utils.DateUtils;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.*;

import static com.intellij.psi.util.PsiTypesUtil.getDefaultValueOfType;
import static com.intellij.psi.util.PsiUtil.*;
import static java.util.Objects.isNull;

public class Java2JsonAction extends AnAction {

    private final NotificationGroup notificationGroup;

    public Java2JsonAction() {
        notificationGroup = new NotificationGroup("Java2Json.NotificationGroup", NotificationDisplayType.BALLOON, true);

        normalTypes.put("String", "");
        normalTypes.put("Boolean", false);
        normalTypes.put("Byte", 0);
        normalTypes.put("Short", (short) 0);
        normalTypes.put("Integer", 0);
        normalTypes.put("Long", 0L);
        normalTypes.put("Float", 0.0F);
        normalTypes.put("Double", 0.0D);
        normalTypes.put("BigDecimal", 0.0);
//        normalTypes.put("Date", new Date());
    }

    @NonNls
    private final Map<String, Object> normalTypes = new HashMap<>();

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
                    selectedClass.getName() + " copied to clipboard in JSON.", NotificationType.INFORMATION);
            Notifications.Bus.notify(success, project);
        } catch (Exception ex) {
            Notification error = notificationGroup.createNotification("Convert to JSON failed.", NotificationType.ERROR);
            Notifications.Bus.notify(error, project);
        }
    }

    public LinkedKeyValueMemory getFields(PsiClass psiClass) {
        LinkedKeyValueMemory memory = new LinkedKeyValueMemory();

        if (isNull(psiClass)) {
            return memory;
        }

        for (PsiField field : psiClass.getAllFields()) {
            //  exclude static field
            if (field.hasModifier(JvmModifier.STATIC)) {
                continue;
            }

            PsiType type = field.getType();
            String name = field.getName();

            if (type instanceof PsiPrimitiveType) {
                memory.set(name, getDefaultValueOfType(type));
                continue;
            }

            String fieldTypeName = type.getPresentableText();
            if ("Date".equals(fieldTypeName)) {
                memory.set(name, "");
                PsiAnnotation anno = field.getAnnotation(JsonFormat.class.getName());
                if (anno != null) {
                    PsiAnnotationMemberValue val = anno.findDeclaredAttributeValue("pattern");
                    if (val != null && !"".equals(val.getText())) {
                        // format current date
                        String pattern = val.getText().substring(1, val.getText().length() - 1);
                        memory.set(name, DateUtils.formatDate(new Date(), pattern));
                    }
                }
            } else if ("String".equals(fieldTypeName)) {
                memory.set(name, name);
            } else if (normalTypes.containsKey(fieldTypeName)) {
                memory.set(name, normalTypes.get(fieldTypeName));
            } else if (type instanceof PsiArrayType) {
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
            } else {
                List<PsiType> types = Arrays.asList(type.getSuperTypes());
                if (Optional.of(types).orElse(Collections.emptyList())
                        .stream().anyMatch(e -> e.getPresentableText().startsWith("Collection"))) {
                    PsiType iterableType = extractIterableTypeParameter(type, false);
                    PsiClass iterableClass = resolveClassInClassTypeOnly(iterableType);
                    ArrayList list = new ArrayList<>();
                    String classTypeName = iterableClass.getName();
                    if (normalTypes.containsKey(classTypeName)) {
                        list.add(normalTypes.get(classTypeName));
                    } else {
                        list.add(this.getFields(iterableClass));
                    }
                    memory.set(name, list);
                } else {
                    memory.set(name, "");
                }
            }

        }

        return memory;
    }


}
