package com.fixutils.ui;

import com.fixutils.dictionary.FixDictionaryService;
import com.fixutils.dictionary.FixFieldDescriptor;
import com.fixutils.parser.FixMessageParser;
import com.fixutils.parser.TagValuePair;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.Map;

public class FixParserPanel extends JPanel {
    private final transient Project project;
    private final transient FixDictionaryService dictionaryService;

    // UI Components
    private JTextArea messageInput;
    private JRadioButton pipeRadio;
    private JRadioButton caretRadio;
    private JRadioButton tildeRadio;
    private JRadioButton sohRadio;
    private JRadioButton customRadio;
    private JTextField customSeparatorField;
    private JComboBox<String> dictionaryCombo;
    
    private FixTableModel tableModel;
    private final transient Timer parseTimer;
    private boolean isUpdatingUi = false;

    private static final String SOH = "\u0001";
    private static final String PIPE = "|";
    private static final String CARET = "^";
    private static final String TILDE = "~";

    public FixParserPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.dictionaryService = ApplicationManager.getApplication().getService(FixDictionaryService.class);

        // Timer for debouncing text changes
        this.parseTimer = new Timer(300, e -> performParse());
        this.parseTimer.setRepeats(false);

        initUi();
    }

    private void initUi() {
        JBTable resultTable;
        // --- NORTH: Input and Controls ---
        JPanel topPanel = new JPanel(new BorderLayout());

        // Input Area
        messageInput = new JTextArea(5, 50);
        messageInput.setLineWrap(true);
        // Hint label pseudo-implementation via tooltip for simplicity, or just let it be blank
        messageInput.setToolTipText("Paste FIX Message here. e.g. 8=FIX.4.1|9=857|...");
        messageInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) {
                    e.consume();
                    performParse();
                }
            }
        });
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("FIX Message"));
        inputPanel.add(new JBScrollPane(messageInput), BorderLayout.CENTER);

        messageInput.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                triggerAutoParse();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                triggerAutoParse();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                triggerAutoParse();
            }
        });

        topPanel.add(inputPanel, BorderLayout.CENTER);

        // Controls Area (Separators + Dictionaries + Parse Button)
        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));

        // Separator Row
        JPanel separatorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        separatorPanel.add(new JLabel("Separator: "));

        pipeRadio = new JRadioButton("| Pipe", true);
        caretRadio = new JRadioButton("^ Caret");
        tildeRadio = new JRadioButton("~ Tilde");
        sohRadio = new JRadioButton("SOH (\\x01)");
        customRadio = new JRadioButton("Custom:");
        customSeparatorField = new JTextField(3);
        customSeparatorField.setEnabled(false);

        ButtonGroup sepGroup = new ButtonGroup();
        sepGroup.add(pipeRadio);
        sepGroup.add(caretRadio);
        sepGroup.add(tildeRadio);
        sepGroup.add(sohRadio);
        sepGroup.add(customRadio);

        customRadio.addChangeListener(e -> {
            boolean isCustom = customRadio.isSelected();
            customSeparatorField.setEnabled(isCustom);
            if (isCustom) {
                triggerAutoParse();
            } else {
                performParse();
            }
        });

        // Add action listener to all radio buttons for instant parsing on change
        java.awt.event.ActionListener radioListener = e -> performParse();
        pipeRadio.addActionListener(radioListener);
        caretRadio.addActionListener(radioListener);
        tildeRadio.addActionListener(radioListener);
        sohRadio.addActionListener(radioListener);
        customRadio.addActionListener(radioListener);

        customSeparatorField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                triggerAutoParse();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                triggerAutoParse();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                triggerAutoParse();
            }
        });

        separatorPanel.add(pipeRadio);
        separatorPanel.add(caretRadio);
        separatorPanel.add(tildeRadio);
        separatorPanel.add(sohRadio);
        separatorPanel.add(customRadio);
        separatorPanel.add(customSeparatorField);

        controlsPanel.add(separatorPanel);

        // Dictionary Row
        JPanel dictPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dictPanel.add(new JLabel("Dictionary: "));

        dictionaryCombo = new ComboBox<>();
        refreshDictionaryCombo();
        dictionaryCombo.addActionListener(e -> performParse());

        // Select FIX41 by default if it exists, else index 0
        dictionaryCombo.setSelectedItem("FIX41");

        JButton browseButton = new JButton("Browse for dictionary...");
        browseButton.addActionListener(e -> browseExternalDictionary());

        JButton parseButton = new JButton("Parse");
        parseButton.addActionListener(e -> performParse());

        dictPanel.add(dictionaryCombo);
        dictPanel.add(browseButton);
        dictPanel.add(Box.createHorizontalStrut(20));
        dictPanel.add(parseButton);

        controlsPanel.add(dictPanel);

        topPanel.add(controlsPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // --- CENTER: Result Table ---
        tableModel = new FixTableModel();
        resultTable = new JBTable(tableModel);

        // Setup table sorting
        TableRowSorter<FixTableModel> sorter = new TableRowSorter<>(tableModel);
        // Make Tag column sort as integers
        sorter.setComparator(0, (o1, o2) -> {
            try {
                return Integer.compare(Integer.parseInt((String) o1), Integer.parseInt((String) o2));
            } catch (Exception e) {
                return ((String) o1).compareTo((String) o2);
            }
        });
        resultTable.setRowSorter(sorter);

        // Custom renderer for row styling
        resultTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                int modelRow = table.convertRowIndexToModel(row);
                if (tableModel.isUnknownTag(modelRow)) {
                    c.setFont(c.getFont().deriveFont(Font.ITALIC));
                    c.setForeground(UIManager.getColor("Label.disabledForeground"));
                } else {
                    c.setFont(c.getFont().deriveFont(Font.PLAIN));
                    c.setForeground(table.getForeground());
                }

                if (column == 1 || column == 3) {
                    ((JLabel) c).setToolTipText(value != null ? value.toString() : "");
                }

                return c;
            }
        });

        resultTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(200);

        add(new JBScrollPane(resultTable), BorderLayout.CENTER);
    }

    private void refreshDictionaryCombo() {
        String selected = (String) dictionaryCombo.getSelectedItem();
        dictionaryCombo.removeAllItems();
        List<String> dicts = dictionaryService.getDisplayNames();
        for (String d : dicts) {
            dictionaryCombo.addItem(d);
        }
        if (selected != null && dicts.contains(selected)) {
            dictionaryCombo.setSelectedItem(selected);
        } else if (!dicts.isEmpty()) {
            dictionaryCombo.setSelectedIndex(0);
        }
    }

    private void browseExternalDictionary() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
        descriptor.withFileFilter(vf -> "xml".equalsIgnoreCase(vf.getExtension()));

        VirtualFile[] files = FileChooserFactory.getInstance().createFileChooser(descriptor, project, this)
                .choose(project);

        if (files.length > 0) {
            File file = new File(files[0].getPath());
            if (dictionaryService.loadExternal(file)) {
                refreshDictionaryCombo();
                dictionaryCombo.setSelectedItem(file.getName());
                performParse();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to load dictionary from " + file.getName(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void performParse() {
        if (isUpdatingUi) return;
        if (parseTimer.isRunning()) {
            parseTimer.stop();
        }

        String message = messageInput.getText();
        if (message == null || message.trim().isEmpty()) {
            // Clear table if input is empty
            ApplicationManager.getApplication().invokeLater(() -> {
                tableModel.setData(List.of(), Map.of());
            });
            return;
        }

        // Try auto-detection if it's a FIX message
        if (message.startsWith("8=")) {
            autoDetectSettings(message);
        }

        String delimiter = getSelectedDelimiter();
        String dictName = (String) dictionaryCombo.getSelectedItem();

        if (delimiter == null || delimiter.isEmpty()) {
            return;
        }

        // Run parsing logic on pooled thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<TagValuePair> pairs = FixMessageParser.parse(message, delimiter);
            Map<Integer, FixFieldDescriptor> dict = dictName != null
                    ? dictionaryService.getDictionary(dictName)
                    : null;

            // Update UI back on EDT
            ApplicationManager.getApplication().invokeLater(() -> {
                tableModel.setData(pairs, dict);
            });
        });
    }

    private void triggerAutoParse() {
        if (isUpdatingUi) return;
        if (parseTimer.isRunning()) {
            parseTimer.restart();
        } else {
            parseTimer.start();
        }
    }

    private void autoDetectSettings(String message) {
        String detectedDelimiter = identifyDelimiter(message);
        if (detectedDelimiter == null) return;

        String fixVersion = extractFixVersion(message, detectedDelimiter);

        // Update UI on EDT
        isUpdatingUi = true;
        try {
            updateDelimiterSelection(detectedDelimiter);
            if (fixVersion != null) {
                String dictToSelect = mapFixVersionToDict(fixVersion);
                if (dictToSelect != null) {
                    dictionaryCombo.setSelectedItem(dictToSelect);
                }
            }
        } finally {
            isUpdatingUi = false;
        }
    }

    private String identifyDelimiter(String message) {
        // Find delimiter: check what's between BeginString and BodyLength
        // Standard pattern: 8=FIX.X.Y<DELIM>9=
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^8=FIX\\.[0-9a-zA-Z.]+(\\D)9=");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Fallback: check most common delimiters
        if (message.contains(SOH)) return SOH;
        if (message.contains(PIPE)) return PIPE;
        if (message.contains(CARET)) return CARET;
        if (message.contains(TILDE)) return TILDE;

        return null;
    }

    private String extractFixVersion(String message, String delimiter) {
        int eqPos = message.indexOf('=');
        int delimPos = message.indexOf(delimiter, eqPos);
        if (delimPos > eqPos) {
            return message.substring(eqPos + 1, delimPos).trim();
        }
        return null;
    }

    private void updateDelimiterSelection(String delimiter) {
        if (PIPE.equals(delimiter)) pipeRadio.setSelected(true);
        else if (CARET.equals(delimiter)) caretRadio.setSelected(true);
        else if (TILDE.equals(delimiter)) tildeRadio.setSelected(true);
        else if (SOH.equals(delimiter)) sohRadio.setSelected(true);
        else {
            customRadio.setSelected(true);
            customSeparatorField.setText(delimiter);
        }
    }

    private String mapFixVersionToDict(String version) {
        // Basic mapping logic
        if (version.startsWith("FIX.4.0")) return "FIX40";
        if (version.startsWith("FIX.4.1")) return "FIX41";
        if (version.startsWith("FIX.4.2")) return "FIX42";
        if (version.startsWith("FIX.4.3")) return "FIX43";
        if (version.startsWith("FIX.4.4")) return "FIX44";
        if (version.startsWith("FIX.5.0SP1")) return "FIX50SP1";
        if (version.startsWith("FIX.5.0SP2")) return "FIX50SP2";
        if (version.startsWith("FIX.5.0")) return "FIX50";
        if (version.startsWith("FIXT.1.1")) return "FIXT11";
        return null;
    }

    private String getSelectedDelimiter() {
        if (pipeRadio.isSelected()) return PIPE;
        if (caretRadio.isSelected()) return CARET;
        if (tildeRadio.isSelected()) return TILDE;
        if (sohRadio.isSelected()) return SOH;
        if (customRadio.isSelected()) return customSeparatorField.getText();
        return PIPE; // fallback
    }
}
