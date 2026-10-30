package com.aicodereview.action;

import com.aicodereview.config.I18n;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fallback picker used when the action is invoked outside the commit tool window.
 */
public final class ChangeSelectionDialog extends DialogWrapper {

    private final Project project;
    private final List<Change> changes;
    private final List<JCheckBox> checkBoxes = new ArrayList<>();
    private final JPanel listPanel = new JPanel();

    public ChangeSelectionDialog(@NotNull Project project, @NotNull List<Change> changes) {
        super(project, true);
        this.project = project;
        this.changes = new ArrayList<>(changes);
        this.changes.sort(Comparator.comparing(change -> ChangePaths.relativePath(project, change)));
        setTitle(I18n.t("选择要审查的更改", "Select changes to review"));
        setOKButtonText(I18n.t("开始审查", "Start review"));
        setCancelButtonText(I18n.t("取消", "Cancel"));
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(JBUI.Borders.empty(8));
        root.add(new JBLabel(I18n.t("选择要发送给 AI 的 Git 待提交文件：",
                "Select the pending Git files to send to AI:")), BorderLayout.NORTH);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        for (Change change : changes) {
            JCheckBox box = new JCheckBox(ChangePaths.describe(project, change), true);
            box.setToolTipText(ChangePaths.relativePath(project, change));
            checkBoxes.add(box);
            listPanel.add(box);
            listPanel.add(Box.createVerticalStrut(2));
        }
        listPanel.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JBScrollPane(listPanel);
        scrollPane.setPreferredSize(new Dimension(780, 460));
        root.add(scrollPane, BorderLayout.CENTER);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        javax.swing.JButton selectAll = new javax.swing.JButton(I18n.t("全选", "Select all"));
        javax.swing.JButton selectNone = new javax.swing.JButton(I18n.t("全不选", "Select none"));
        selectAll.addActionListener(e -> setAllSelected(true));
        selectNone.addActionListener(e -> setAllSelected(false));
        tools.add(selectAll);
        tools.add(selectNone);
        root.add(tools, BorderLayout.SOUTH);

        return root;
    }

    private void setAllSelected(boolean selected) {
        for (JCheckBox checkBox : checkBoxes) {
            checkBox.setSelected(selected);
        }
    }

    public @NotNull List<Change> getSelectedChanges() {
        List<Change> selected = new ArrayList<>();
        for (int i = 0; i < checkBoxes.size(); i++) {
            if (checkBoxes.get(i).isSelected()) {
                selected.add(changes.get(i));
            }
        }
        return selected;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (getSelectedChanges().isEmpty()) {
            return new ValidationInfo(I18n.t("请至少选择一个文件。", "Select at least one file."));
        }
        return null;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return checkBoxes.isEmpty() ? null : checkBoxes.get(0);
    }
}
