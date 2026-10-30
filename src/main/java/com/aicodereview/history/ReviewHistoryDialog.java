package com.aicodereview.history;

import com.aicodereview.config.I18n;
import com.aicodereview.output.ReportWriter;
import com.aicodereview.review.ReviewFindingsDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists locally retained review reports. Double-click opens the full Markdown
 * report; "Findings" opens the clickable file:line list.
 */
public final class ReviewHistoryDialog extends DialogWrapper {

    private final Project project;
    private final ReviewHistoryService history;
    private final List<ReviewHistoryEntry> entries;
    private final JBTable table;

    public ReviewHistoryDialog(@NotNull Project project) {
        super(project, true);
        this.project = project;
        this.history = ReviewHistoryService.getInstance(project);
        this.entries = new ArrayList<>(history.list());
        this.table = new JBTable(new HistoryModel(entries));
        setTitle(I18n.t("AI 审查历史", "AI Review History"));
        setOKButtonText(I18n.t("关闭", "Close"));
        configureTable();
        init();
    }

    private void configureTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(JBUI.scale(24));
        table.getColumnModel().getColumn(0).setPreferredWidth(JBUI.scale(150));
        table.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(420));
        table.getColumnModel().getColumn(2).setPreferredWidth(JBUI.scale(180));
        table.getColumnModel().getColumn(3).setPreferredWidth(JBUI.scale(70));
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedReport();
                }
            }
        });
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(JBUI.Borders.empty(8));
        root.add(new JBLabel(I18n.t(
                "选择一次审查记录：双击打开报告，或点击“问题定位”跳转到文件行。",
                "Select a review. Double-click to open the report, or use \"Findings\" to jump to file lines.")),
                BorderLayout.NORTH);
        JBScrollPane scrollPane = new JBScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(900, 420));
        root.add(scrollPane, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton open = new JButton(I18n.t("打开报告", "Open report"));
        open.addActionListener(e -> openSelectedReport());
        JButton findings = new JButton(I18n.t("问题定位", "Findings"));
        findings.addActionListener(e -> openSelectedFindings());
        JButton delete = new JButton(I18n.t("删除", "Delete"));
        delete.addActionListener(e -> deleteSelected());
        JButton clear = new JButton(I18n.t("清空历史", "Clear all"));
        clear.addActionListener(e -> clearAll());
        JButton folder = new JButton(I18n.t("打开历史目录", "Open folder"));
        folder.addActionListener(e -> openHistoryFolder());
        actions.add(open);
        actions.add(findings);
        actions.add(delete);
        actions.add(clear);
        actions.add(folder);
        if (entries.isEmpty()) {
            actions.add(new JBLabel(I18n.t("还没有本地审查记录。", "No local review history yet.")));
        }
        root.add(actions, BorderLayout.SOUTH);
        return root;
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction()};
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return table;
    }

    private ReviewHistoryEntry selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= entries.size() ? null : entries.get(row);
    }

    private void openSelectedReport() {
        ReviewHistoryEntry entry = selected();
        if (entry == null) {
            return;
        }
        try {
            history.read(entry);
            ReportWriter.openInEditor(project, history.reportPath(entry));
        } catch (IOException e) {
            Messages.showErrorDialog(project,
                    I18n.t("读取历史报告失败：" + e.getMessage(),
                            "Failed to read the history report: " + e.getMessage()),
                    I18n.t("AI 审查历史", "AI Review History"));
        }
    }

    private void openSelectedFindings() {
        ReviewHistoryEntry entry = selected();
        if (entry == null) {
            return;
        }
        try {
            String report = history.read(entry);
            Path path = history.reportPath(entry);
            new ReviewFindingsDialog(project, report, path).show();
        } catch (IOException e) {
            Messages.showErrorDialog(project,
                    I18n.t("读取历史报告失败：" + e.getMessage(),
                            "Failed to read the history report: " + e.getMessage()),
                    I18n.t("AI 审查历史", "AI Review History"));
        }
    }

    private void deleteSelected() {
        ReviewHistoryEntry entry = selected();
        if (entry == null) {
            return;
        }
        try {
            history.delete(entry);
            entries.remove(entry);
            ((HistoryModel) table.getModel()).fireTableDataChanged();
        } catch (IOException e) {
            Messages.showErrorDialog(project, e.getMessage(), I18n.t("删除失败", "Delete failed"));
        }
    }

    private void clearAll() {
        if (Messages.showYesNoDialog(project,
                I18n.t("确定清空所有本地审查历史？", "Clear all local review history?"),
                I18n.t("AI 审查历史", "AI Review History"),
                Messages.getWarningIcon()) != Messages.YES) {
            return;
        }
        try {
            history.clear();
            entries.clear();
            ((HistoryModel) table.getModel()).fireTableDataChanged();
        } catch (IOException e) {
            Messages.showErrorDialog(project, e.getMessage(), I18n.t("清空失败", "Clear failed"));
        }
    }

    private void openHistoryFolder() {
        try {
            Desktop.getDesktop().open(history.getDirectory().toFile());
        } catch (IOException | UnsupportedOperationException e) {
            Messages.showInfoMessage(project, history.getDirectory().toString(),
                    I18n.t("历史目录", "History folder"));
        }
    }

    private static final class HistoryModel extends AbstractTableModel {
        private final List<ReviewHistoryEntry> entries;
        private final String[] columns = {
                I18n.t("时间", "Time"),
                I18n.t("范围", "Scope"),
                I18n.t("模型", "Model"),
                I18n.t("文件数", "Files")
        };

        private HistoryModel(List<ReviewHistoryEntry> entries) {
            this.entries = entries;
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ReviewHistoryEntry entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.displayTime();
                case 1 -> entry.scope;
                case 2 -> entry.model;
                case 3 -> entry.fileCount;
                default -> "";
            };
        }
    }
}
