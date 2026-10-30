package com.aicodereview.config;

import com.aicodereview.llm.AiClient;
import com.aicodereview.llm.AiCompletion;
import com.aicodereview.llm.AiConnection;
import com.aicodereview.llm.AiException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.net.URI;
import java.net.URISyntaxException;

public final class AiReviewConfigurable implements Configurable {

    private final JComboBox<String> uiLanguageCombo =
            new JComboBox<>(new String[]{"中文", "English"});
    private final JBTextField endpointField = new JBTextField(48);
    private final JBTextField modelField = new JBTextField(28);
    private final JBPasswordField apiKeyField = new JBPasswordField();
    private final JCheckBox clearKeyCheckBox;
    private final JCheckBox noApiKeyCheckBox;
    private final JBLabel keyStatusLabel = new JBLabel();
    private final JBTextField apiKeyHeaderField = new JBTextField(20);
    private final JBTextField apiKeyPrefixField = new JBTextField(16);
    private final JTextArea extraHeadersArea = new JTextArea(3, 48);
    private final JSpinner timeoutSpinner = new JSpinner(new SpinnerNumberModel(180, 10, 600, 10));
    private final JSpinner maxTokensSpinner = new JSpinner(new SpinnerNumberModel(4096, 256, 32768, 256));

    private final JBTextField outputFileField = new JBTextField(28);
    private final JBTextField languageField = new JBTextField(16);
    private final JSpinner temperatureSpinner = new JSpinner(new SpinnerNumberModel(0.1, 0.0, 2.0, 0.1));
    private final JSpinner maxDiffCharsSpinner =
            new JSpinner(new SpinnerNumberModel(60000, 4000, 400000, 1000));
    private final JCheckBox openReportCheckBox;
    private final JTextArea promptArea = new JTextArea(20, 100);

    private final JButton testButton;
    private final JBLabel testStatusLabel = new JBLabel(" ");
    private final JButton resetPromptButton;

    private JTabbedPane tabs;
    private JComboBox<AiProviderPreset> presetCombo;
    private boolean loading;
    private volatile boolean disposed;

    public AiReviewConfigurable() {
        clearKeyCheckBox = new JCheckBox(t("清除已保存的 Key", "Clear saved key"));
        noApiKeyCheckBox = new JCheckBox(t("接口无需 API Key（本地/内网网关）",
                "No API key required (local/intranet gateway)"));
        openReportCheckBox = new JCheckBox(t("完成后自动打开报告", "Open the report automatically"));
        testButton = new JButton(t("测试连接", "Test connection"));
        resetPromptButton = new JButton(t("恢复默认提示词", "Reset prompt"));
        configureFields();
    }

    private static String t(String zh, String en) {
        return I18n.t(zh, en);
    }

    @Override
    public @Nls String getDisplayName() {
        return "AI Code Review";
    }

    @Override
    public @Nullable String getHelpTopic() {
        return null;
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (tabs == null) {
            tabs = new JTabbedPane();
            tabs.addTab(t("连接设置", "Connection"), buildConnectionTab());
            tabs.addTab(t("提示词与输出", "Prompt & Output"), buildPromptTab());
            tabs.setBorder(JBUI.Borders.empty(8));
        }
        return tabs;
    }

    private JComponent buildConnectionTab() {
        JPanel keyPanel = new JPanel(new BorderLayout(8, 0));
        keyPanel.add(apiKeyField, BorderLayout.CENTER);
        keyPanel.add(clearKeyCheckBox, BorderLayout.EAST);

        JPanel testPanel = new JPanel(new BorderLayout(8, 0));
        testPanel.add(testButton, BorderLayout.WEST);
        testPanel.add(testStatusLabel, BorderLayout.CENTER);

        JPanel headers = new JPanel(new GridLayout(1, 2, 8, 0));
        headers.add(labeled(t("认证 Header", "Auth header"), apiKeyHeaderField));
        headers.add(labeled(t("Key 前缀", "Key prefix"), apiKeyPrefixField));

        extraHeadersArea.setFont(monospacedFont(12));
        extraHeadersArea.setLineWrap(true);
        extraHeadersArea.setWrapStyleWord(true);

        JPanel form = FormBuilder.createFormBuilder()
                .addComponent(new TitledSeparator(t("通用", "General")))
                .addLabeledComponent(t("界面语言", "UI language"), uiLanguageCombo)
                .addComponent(new JBLabel(t(
                        "保存后重新打开此设置页，界面文案才会切换。",
                        "Reopen this settings page after saving to apply the UI language.")))
                .addComponent(new TitledSeparator(t("服务", "Service")))
                .addLabeledComponent(t("服务预设", "Provider preset"), presetComboBox())
                .addLabeledComponent(t("接口地址（含端口）", "Endpoint (including port)"), endpointField)
                .addLabeledComponent(t("模型", "Model"), modelField)
                .addLabeledComponent(t("API Key", "API key"), keyPanel)
                .addComponent(keyStatusLabel)
                .addComponent(noApiKeyCheckBox)
                .addComponent(new TitledSeparator(t("高级", "Advanced")))
                .addComponent(headers)
                .addLabeledComponent(t("额外请求头", "Extra headers"), new JBScrollPane(extraHeadersArea))
                .addLabeledComponent(t("超时（秒）", "Timeout (seconds)"), timeoutSpinner)
                .addLabeledComponent(t("最大输出 tokens", "Max output tokens"), maxTokensSpinner)
                .addComponent(testPanel)
                .addComponent(new JBLabel(" "))
                .getPanel();

        endpointField.getEmptyText().setText(t(
                "例如 https://api.deepseek.com/chat/completions",
                "e.g. https://api.deepseek.com/chat/completions"));
        extraHeadersArea.setToolTipText(t(
                "每行一个 Header: Value；本地 Ollama 可留空",
                "One Header: Value per line; leave empty for local Ollama"));
        testStatusLabel.setForeground(UIUtil.getLabelForeground());
        testButton.addActionListener(e -> runConnectionTest());
        clearKeyCheckBox.addActionListener(e -> {
            if (clearKeyCheckBox.isSelected()) {
                apiKeyField.setText("");
            }
            updateKeyFieldEnabled();
            refreshKeyStatus();
        });
        noApiKeyCheckBox.addActionListener(e -> {
            if (noApiKeyCheckBox.isSelected()) {
                apiKeyField.setText("");
            }
            updateKeyFieldEnabled();
            refreshKeyStatus();
        });
        uiLanguageCombo.addActionListener(e -> onUiLanguageChanged());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(form, BorderLayout.NORTH);
        wrapper.setBorder(JBUI.Borders.empty(8));
        return new JBScrollPane(wrapper);
    }

    private JComponent buildPromptTab() {
        promptArea.setFont(monospacedFont(12));
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(false);
        promptArea.setTabSize(2);

        outputFileField.getEmptyText().setText(AiReviewSettings.DEFAULT_OUTPUT_FILE);
        languageField.getEmptyText().setText(t("中文", "English"));

        JPanel promptHeader = new JPanel(new BorderLayout(8, 0));
        promptHeader.add(new JBLabel(t("提示词模板", "Prompt template")), BorderLayout.WEST);
        promptHeader.add(resetPromptButton, BorderLayout.EAST);

        JBScrollPane promptScroll = new JBScrollPane(promptArea);
        promptScroll.setPreferredSize(new Dimension(900, 380));
        promptScroll.setBorder(BorderFactory.createLineBorder(JBColor.border()));

        JPanel form = FormBuilder.createFormBuilder()
                .addComponent(new TitledSeparator(t("输出", "Output")))
                .addLabeledComponent(t("输出文件（项目根目录）", "Output file (project root)"), outputFileField)
                .addLabeledComponent(t("报告语言", "Report language"), languageField)
                .addLabeledComponent(t("温度", "Temperature"), temperatureSpinner)
                .addLabeledComponent(t("最大 diff 字符数", "Max diff characters"), maxDiffCharsSpinner)
                .addComponent(openReportCheckBox)
                .addComponent(new TitledSeparator(t("提示词", "Prompt")))
                .addComponent(promptHeader)
                .addComponent(promptScroll)
                .addComponent(new JBLabel(t(
                        "可用占位符：{{PROJECT_NAME}}、{{SCOPE}}、{{FILE_LIST}}、{{LANGUAGE}}、"
                                + "{{DATE}}、{{DIFF}}。若模板不含 {{DIFF}}，程序会自动追加代码变更。",
                        "Placeholders: {{PROJECT_NAME}}, {{SCOPE}}, {{FILE_LIST}}, {{LANGUAGE}}, "
                                + "{{DATE}}, {{DIFF}}. If {{DIFF}} is missing, the code changes are appended.")))
                .getPanel();

        resetPromptButton.addActionListener(e -> {
            if (Messages.showYesNoDialog(
                    t("确定恢复内置提示词模板？当前修改将被覆盖。",
                            "Reset to the built-in prompt template? Your current edits will be overwritten."),
                    t("恢复默认提示词", "Reset prompt"), Messages.getQuestionIcon()) == Messages.YES) {
                promptArea.setText(DefaultPrompt.forLanguage(selectedUiLanguage()));
                promptArea.setCaretPosition(0);
            }
        });

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(form, BorderLayout.NORTH);
        wrapper.setBorder(JBUI.Borders.empty(8));
        return new JBScrollPane(wrapper);
    }

    private JComboBox<AiProviderPreset> presetComboBox() {
        presetCombo = new JComboBox<>(AiProviderPreset.values());
        presetCombo.addActionListener(e -> {
            AiProviderPreset preset = (AiProviderPreset) presetCombo.getSelectedItem();
            if (!loading && preset != null && preset != AiProviderPreset.CUSTOM) {
                endpointField.setText(preset.endpoint());
                modelField.setText(preset.model());
            }
        });
        return presetCombo;
    }

    private static JPanel labeled(String label, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.add(new JBLabel(label), BorderLayout.WEST);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private static Font monospacedFont(int size) {
        return new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(size));
    }

    private void configureFields() {
        apiKeyHeaderField.getEmptyText().setText("Authorization");
        apiKeyPrefixField.getEmptyText().setText("Bearer ");
        extraHeadersArea.setText("");
        extraHeadersArea.setBorder(JBUI.Borders.empty(4));
        promptArea.setBorder(JBUI.Borders.empty(6));
        JSpinner.NumberEditor temperatureEditor = new JSpinner.NumberEditor(temperatureSpinner, "0.00");
        temperatureSpinner.setEditor(temperatureEditor);
        JSpinner.NumberEditor timeoutEditor = new JSpinner.NumberEditor(timeoutSpinner, "#");
        timeoutSpinner.setEditor(timeoutEditor);
    }

    @Override
    public boolean isModified() {
        AiReviewSettings settings = AiReviewSettings.getInstance();
        AiReviewSettings.State current = settings.getState();
        AiReviewSettings.State edited = buildState();
        AiReviewSettings.normalize(edited);

        if (!equal(current.endpoint, edited.endpoint)
                || !equal(current.model, edited.model)
                || !equal(current.apiKeyHeader, edited.apiKeyHeader)
                || !equal(current.apiKeyPrefix, edited.apiKeyPrefix)
                || !equal(current.extraHeaders, edited.extraHeaders)
                || current.noApiKey != edited.noApiKey
                || !equal(current.uiLanguage, edited.uiLanguage)
                || !equal(current.outputFile, edited.outputFile)
                || !equal(current.language, edited.language)
                || Double.compare(current.temperature, edited.temperature) != 0
                || current.maxOutputTokens != edited.maxOutputTokens
                || current.timeoutSeconds != edited.timeoutSeconds
                || current.maxDiffChars != edited.maxDiffChars
                || current.openReport != edited.openReport
                || !equal(current.promptTemplate, edited.promptTemplate)) {
            return true;
        }

        return clearKeyCheckBox.isSelected() || !new String(apiKeyField.getPassword()).isEmpty();
    }

    @Override
    public void apply() throws ConfigurationException {
        AiReviewSettings settings = AiReviewSettings.getInstance();
        AiReviewSettings.State next = buildState();
        AiReviewSettings.normalize(next);
        validate(next);
        settings.applyFrom(next);

        if (clearKeyCheckBox.isSelected()) {
            ApiKeyStore.clear();
        } else if (!noApiKeyCheckBox.isSelected()) {
            String typedKey = new String(apiKeyField.getPassword()).trim();
            if (!typedKey.isEmpty()) {
                ApiKeyStore.set(typedKey);
            }
        }

        apiKeyField.setText("");
        clearKeyCheckBox.setSelected(false);
        updateKeyFieldEnabled();
        refreshKeyStatus();
        reset();
    }

    private void validate(AiReviewSettings.State state) throws ConfigurationException {
        validateConnection(state);
        if (state.promptTemplate == null || state.promptTemplate.isBlank()) {
            throw new ConfigurationException(t("提示词模板不能为空。", "The prompt template must not be empty."));
        }
    }

    private void validateConnection(AiReviewSettings.State state) throws ConfigurationException {
        if (state.endpoint == null || state.endpoint.isBlank()) {
            throw new ConfigurationException(t("请填写接口地址。", "Please enter the endpoint."));
        }
        try {
            URI uri = new URI(state.endpoint);
            if (uri.getScheme() == null
                    || (!"http".equalsIgnoreCase(uri.getScheme())
                    && !"https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new ConfigurationException(t("接口地址必须是完整的 http:// 或 https:// URL。",
                        "The endpoint must be a complete http:// or https:// URL."));
            }
        } catch (URISyntaxException e) {
            throw new ConfigurationException(t("接口地址格式不正确。", "The endpoint format is invalid."));
        }
        if (state.model == null || state.model.isBlank()) {
            throw new ConfigurationException(t("请填写模型名称。", "Please enter the model name."));
        }
    }

    @Override
    public void reset() {
        AiReviewSettings settings = AiReviewSettings.getInstance();
        AiReviewSettings.State state = settings.getState();

        loading = true;
        endpointField.setText(state.endpoint);
        modelField.setText(state.model);
        apiKeyHeaderField.setText(state.apiKeyHeader);
        apiKeyPrefixField.setText(state.apiKeyPrefix);
        extraHeadersArea.setText(state.extraHeaders);
        outputFileField.setText(state.outputFile);
        languageField.setText(state.language);
        timeoutSpinner.setValue(state.timeoutSeconds);
        maxTokensSpinner.setValue(state.maxOutputTokens);
        temperatureSpinner.setValue(state.temperature);
        maxDiffCharsSpinner.setValue(state.maxDiffChars);
        openReportCheckBox.setSelected(state.openReport);
        noApiKeyCheckBox.setSelected(state.noApiKey);
        uiLanguageCombo.setSelectedIndex(I18n.EN.equalsIgnoreCase(state.uiLanguage) ? 1 : 0);
        promptArea.setText(state.promptTemplate);
        promptArea.setCaretPosition(0);

        if (presetCombo != null) {
            AiProviderPreset matching = AiProviderPreset.CUSTOM;
            for (AiProviderPreset preset : AiProviderPreset.values()) {
                if (preset != AiProviderPreset.CUSTOM
                        && preset.endpoint().equals(state.endpoint)
                        && preset.model().equals(state.model)) {
                    matching = preset;
                    break;
                }
            }
            presetCombo.setSelectedItem(matching);
        }

        apiKeyField.setText("");
        clearKeyCheckBox.setSelected(false);
        updateKeyFieldEnabled();
        refreshKeyStatus();
        testStatusLabel.setText(" ");
        testStatusLabel.setToolTipText(null);
        testButton.setEnabled(true);
        loading = false;
    }

    private void onUiLanguageChanged() {
        if (loading) {
            return;
        }
        String code = selectedUiLanguage();
        String currentPrompt = promptArea.getText();
        if (currentPrompt.equals(DefaultPrompt.ZH_TEMPLATE)
                || currentPrompt.equals(DefaultPrompt.EN_TEMPLATE)
                || currentPrompt.isBlank()) {
            promptArea.setText(DefaultPrompt.forLanguage(code));
            promptArea.setCaretPosition(0);
        }
        String language = languageField.getText().trim();
        if (language.isEmpty() || "中文".equals(language) || "English".equalsIgnoreCase(language)) {
            languageField.setText(I18n.EN.equals(code) ? "English" : "中文");
        }
    }

    private String selectedUiLanguage() {
        return uiLanguageCombo.getSelectedIndex() == 1 ? I18n.EN : I18n.ZH;
    }

    private void updateKeyFieldEnabled() {
        boolean noKey = noApiKeyCheckBox.isSelected();
        apiKeyField.setEnabled(!noKey && !clearKeyCheckBox.isSelected());
        clearKeyCheckBox.setEnabled(!noKey);
    }

    private void refreshKeyStatus() {
        if (noApiKeyCheckBox.isSelected()) {
            keyStatusLabel.setText(t("已启用“无需 API Key”，请求不会发送认证 Header。",
                    "No API key: authentication headers will not be sent."));
        } else if (clearKeyCheckBox.isSelected()) {
            keyStatusLabel.setText(t("保存设置后将清除已保存的 API Key。",
                    "The saved API key will be cleared when settings are applied."));
        } else if (ApiKeyStore.hasKey()) {
            keyStatusLabel.setText(t("已保存到 IDE 凭据库；留空表示保持不变。",
                    "Saved in the IDE credential store; leave empty to keep it unchanged."));
        } else {
            keyStatusLabel.setText(t("未保存 API Key；本地 Ollama 等无需鉴权的接口可以勾选上方选项。",
                    "No saved API key. Enable the option above for local Ollama or unauthenticated gateways."));
        }
        keyStatusLabel.setForeground(UIUtil.getLabelForeground());
    }

    @Override
    public void disposeUIResources() {
        disposed = true;
    }

    private AiReviewSettings.State buildState() {
        AiReviewSettings.State state = new AiReviewSettings.State();
        state.endpoint = endpointField.getText().trim();
        state.model = modelField.getText().trim();
        state.apiKeyHeader = apiKeyHeaderField.getText().trim();
        state.apiKeyPrefix = apiKeyPrefixField.getText();
        state.extraHeaders = extraHeadersArea.getText().trim();
        state.noApiKey = noApiKeyCheckBox.isSelected();
        state.uiLanguage = selectedUiLanguage();
        state.outputFile = outputFileField.getText().trim();
        state.language = languageField.getText().trim();
        state.temperature = ((Number) temperatureSpinner.getValue()).doubleValue();
        state.maxOutputTokens = ((Number) maxTokensSpinner.getValue()).intValue();
        state.timeoutSeconds = ((Number) timeoutSpinner.getValue()).intValue();
        state.maxDiffChars = ((Number) maxDiffCharsSpinner.getValue()).intValue();
        state.openReport = openReportCheckBox.isSelected();
        state.promptTemplate = promptArea.getText();
        return state;
    }

    private void runConnectionTest() {
        AiReviewSettings.State state = buildState();
        AiReviewSettings.normalize(state);
        try {
            validateConnection(state);
        } catch (ConfigurationException e) {
            Messages.showErrorDialog(e.getLocalizedMessage(), t("配置不完整", "Incomplete configuration"));
            return;
        }

        String key = null;
        if (!noApiKeyCheckBox.isSelected() && !clearKeyCheckBox.isSelected()) {
            key = new String(apiKeyField.getPassword()).trim();
            if (key.isEmpty()) {
                key = ApiKeyStore.get();
            }
        }
        // Keep the connectivity check short; a stuck test should not look like a frozen dialog.
        state.timeoutSeconds = Math.min(30, Math.max(10, state.timeoutSeconds));
        AiConnection connection = AiConnection.from(state, key);

        testButton.setEnabled(false);
        testStatusLabel.setForeground(UIUtil.getLabelForeground());
        testStatusLabel.setText(t("正在测试连接…", "Testing connection…"));

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String message;
            boolean ok;
            try {
                AiCompletion completion = new AiClient().testConnection(connection);
                ok = true;
                message = t("连接成功：", "Connected: ")
                        + (completion.model() == null ? state.model : completion.model())
                        + t("，", ", ") + completion.elapsedMs() + " ms";
            } catch (AiException e) {
                ok = false;
                message = e.getMessage();
            } catch (RuntimeException | LinkageError e) {
                ok = false;
                message = t("测试失败：", "Test failed: ")
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }

            boolean finalOk = ok;
            String finalMessage = message;
            // Settings is a modal dialog. Use ModalityState.any() so the result is not
            // queued behind it while the label is still showing "Testing…".
            ApplicationManager.getApplication().invokeLater(() -> {
                if (disposed) {
                    return;
                }
                testButton.setEnabled(true);
                testStatusLabel.setForeground(finalOk
                        ? new JBColor(new java.awt.Color(0, 128, 0), new java.awt.Color(106, 185, 106))
                        : JBColor.RED);
                String oneLine = finalMessage == null ? t("未知错误", "Unknown error") : finalMessage.replace('\n', ' ');
                testStatusLabel.setText(oneLine.length() > 120 ? oneLine.substring(0, 120) + "…" : oneLine);
                testStatusLabel.setToolTipText(finalMessage);
                if (!finalOk) {
                    Messages.showErrorDialog(finalMessage, t("连接测试失败", "Connection test failed"));
                }
            }, ModalityState.any());
        });
    }

    private static boolean equal(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }
}
