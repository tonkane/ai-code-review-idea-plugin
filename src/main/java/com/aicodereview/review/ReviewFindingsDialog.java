package com.aicodereview.review;

import com.aicodereview.config.I18n;
import com.aicodereview.output.ReportWriter;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
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
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;

/**
 * Clickable list of findings parsed from the fixed Markdown report. Double-click
 * a row (or use "Go to") to open the file at the cited line.
 */
public final class ReviewFindingsDialog extends DialogWrapper {

    private final Project project;
    private final Path reportPath;
    private final List<ReviewFinding> findings;
    private final JBTable table;
    private final JButton goToButton;

    public ReviewFindingsDialog(@NotNull Project project,
                                @NotNull String report,
                                @NotNull Path reportPath) {
        super(project, true);
        this.project = project;
        this.reportPath = reportPath;
        this.findings = ReviewFindingsParser.parse(report);
        this.table = new JBTable(new FindingsModel(findings));
        this.goToButton = new JButton(I18n.t("定位", "Go to"));
        setTitle(I18n.t("审查问题定位", "Review Findings"));
        setOKButtonText(I18n.t("关闭", "Close"));
        configureTable();
        init();
    }

    private void configureTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(JBUI.scale(24));
        table.getColumnModel().getColumn(0).setPreferredWidth(JBUI.scale(80));
        table.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(100));
        table.getColumnModel().getColumn(2).setPreferredWidth(JBUI.scale(260));
        table.getColumnModel().getColumn(3).setPreferredWidth(JBUI.scale(520));
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateSelected();
                }
            }
        });
        goToButton.addActionListener(e -> navigateSelected());
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(JBUI.Borders.empty(8));
        root.add(new JBLabel(I18n.t(
                "双击问题行，或选中后点击“定位”，跳转到对应文件和行号。",
                "Double-click a finding, or select it and click \"Go to\", to open the cited file and line.")),
                BorderLayout.NORTH);
        JBScrollPane scrollPane = new JBScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(1000, 460));
        root.add(scrollPane, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(goToButton);
        JButton openReport = new JButton(I18n.t("打开报告", "Open report"));
        openReport.addActionListener(e -> ReportWriter.openInEditor(project, reportPath));
        actions.add(openReport);
        if (findings.isEmpty()) {
            actions.add(new JBLabel(I18n.t("报告中未解析到问题位置。",
                    "No file:line findings were found in this report.")));
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

    private void navigateSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= findings.size()) {
            return;
        }
        ReviewFinding finding = findings.get(row);
        VirtualFile base = ProjectUtil.guessProjectDir(project);
        VirtualFile file = base == null ? null : base.findFileByRelativePath(finding.path());
        if (file == null) {
            file = LocalFileSystem.getInstance().findFileByPath(finding.path());
        }
        if (file == null || !file.isValid()) {
            Messages.showInfoMessage(project,
                    I18n.t("找不到文件：" + finding.path(),
                            "File not found: " + finding.path()),
                    I18n.t("无法定位", "Cannot navigate"));
            return;
        }
        new OpenFileDescriptor(project, file, Math.max(0, finding.line() - 1), 0).navigate(true);
    }

    private static final class FindingsModel extends AbstractTableModel {
        private final List<ReviewFinding> findings;
        private final String[] columns = {
                I18n.t("级别", "Severity"),
                I18n.t("维度", "Dimension"),
                I18n.t("位置", "Location"),
                I18n.t("问题", "Issue")
        };

        private FindingsModel(List<ReviewFinding> findings) {
            this.findings = findings;
        }

        @Override
        public int getRowCount() {
            return findings.size();
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
            ReviewFinding finding = findings.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> finding.severity();
                case 1 -> finding.dimension();
                case 2 -> finding.location();
                case 3 -> finding.description();
                default -> "";
            };
        }
    }
}
