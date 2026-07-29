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
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private JLabel dictionaryStatusLabel;
    private JTextField searchField;
    private JBTable resultTable;
    
    private FixTableModel tableModel;
    private final transient Timer parseTimer;
    private boolean isUpdatingUi = false;
    private String lastDetectedVersion = null;
    private String lastDetectedDelimiter = null;

    private String lastSearchText = "";
    private int lastFoundRow = -1;
    private int lastFoundCol = -1;

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
        });

        // Add action listener to all radio buttons for instant parsing on change
        ActionListener radioListener = e -> {
            if (!isUpdatingUi) {
                if (tableModel != null && !tableModel.getData().isEmpty()) {
                    rebuildMessageFromTable();
                }
                performParse();
            }
        };
        pipeRadio.addActionListener(radioListener);
        caretRadio.addActionListener(radioListener);
        tildeRadio.addActionListener(radioListener);
        sohRadio.addActionListener(radioListener);
        customRadio.addActionListener(radioListener);

        customSeparatorField.getDocument().addDocumentListener(new DocumentListener() {
            private void handleCustomSeparatorChange() {
                if (!isUpdatingUi) {
                    if (tableModel != null && !tableModel.getData().isEmpty()) {
                        rebuildMessageFromTable();
                    }
                    triggerAutoParse();
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                handleCustomSeparatorChange();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                handleCustomSeparatorChange();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                handleCustomSeparatorChange();
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

        dictionaryStatusLabel = new JLabel("");
        dictionaryStatusLabel.setFont(dictionaryStatusLabel.getFont().deriveFont(Font.BOLD));

        dictionaryCombo = new ComboBox<>();
        refreshDictionaryCombo();
        dictionaryCombo.addActionListener(e -> {
            updateDictionaryStatus();
            performParse();
        });

        // Select FIX41 by default if it exists, else index 0
        dictionaryCombo.setSelectedItem("FIX41");

        JButton browseButton = new JButton("Browse for dictionary...");
        browseButton.addActionListener(e -> browseExternalDictionary());

        JButton parseButton = new JButton("Parse");
        parseButton.addActionListener(e -> performParse());

        JButton recalculateButton = new JButton("Recalculate Checksum & Length");
        recalculateButton.addActionListener(e -> performRecalculate());

        dictPanel.add(dictionaryCombo);
        dictPanel.add(browseButton);
        dictPanel.add(Box.createHorizontalStrut(20));
        dictPanel.add(parseButton);
        dictPanel.add(Box.createHorizontalStrut(10));
        dictPanel.add(recalculateButton);
        dictPanel.add(Box.createHorizontalStrut(10));
        dictPanel.add(dictionaryStatusLabel);
        dictPanel.add(Box.createHorizontalStrut(20));
        dictPanel.add(new JLabel("Search: "));

        searchField = new JTextField(12);
        searchField.setToolTipText("Search by Tag number, Field Name, or Value");
        searchField.addActionListener(e -> performFindNext());
        dictPanel.add(searchField);

        JButton findButton = new JButton("Find in message");
        findButton.addActionListener(e -> performFindNext());
        dictPanel.add(findButton);

        controlsPanel.add(dictPanel);

        topPanel.add(controlsPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // --- CENTER: Result Table ---
        tableModel = new FixTableModel();
        tableModel.setValueUpdateListener(this::rebuildMessageFromTable);
        resultTable = new JBTable(tableModel);
        resultTable.setCellSelectionEnabled(true);

        AbstractAction copyCellAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int row = resultTable.getSelectedRow();
                int col = resultTable.getSelectedColumn();
                if (row != -1 && col != -1) {
                    Object value = resultTable.getValueAt(row, col);
                    if (value != null) {
                        StringSelection selection = new StringSelection(value.toString());
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                    }
                }
            }
        };

        KeyStroke copyKS = KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        KeyStroke ctrlCKS = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK);
        resultTable.getInputMap(JComponent.WHEN_FOCUSED).put(copyKS, "copyCell");
        resultTable.getInputMap(JComponent.WHEN_FOCUSED).put(ctrlCKS, "copyCell");
        resultTable.getActionMap().put("copyCell", copyCellAction);
        resultTable.getActionMap().put("copy", copyCellAction);

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
            private final Color errorBg = new JBColor(new Color(255, 220, 220), new Color(90, 50, 50));
            private final Color errorFg = new JBColor(new Color(180, 0, 0), new Color(255, 120, 120));

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                int modelRow = table.convertRowIndexToModel(row);
                boolean isUnknown = tableModel.isUnknownTag(modelRow);

                if (isUnknown) {
                    c.setFont(c.getFont().deriveFont(Font.ITALIC));
                    c.setForeground(UIManager.getColor("Label.disabledForeground"));
                } else {
                    c.setFont(c.getFont().deriveFont(Font.PLAIN));
                    c.setForeground(table.getForeground());
                }

                // Reset background
                if (!isSelected) {
                    c.setBackground(table.getBackground());
                }

                // Custom validation styling on "Value" column (index 3)
                if (column == 3 && !isUnknown) {
                    boolean isValid = tableModel.isValueValid(modelRow);
                    if (!isValid) {
                        if (!isSelected) {
                            c.setBackground(errorBg);
                        }
                        c.setForeground(errorFg);
                        ((JLabel) c).setToolTipText(tableModel.getValidationErrorMessage(modelRow));
                    } else {
                        ((JLabel) c).setToolTipText(value != null ? value.toString() : "");
                    }
                } else if (column == 1 || column == 4) {
                    ((JLabel) c).setToolTipText(value != null ? value.toString() : "");
                } else {
                    ((JLabel) c).setToolTipText(null);
                }

                return c;
            }
        });

        resultTable.getColumnModel().getColumn(0).setPreferredWidth(50);  // Tag
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(160); // Field Name
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(70);  // Type
        resultTable.getColumnModel().getColumn(3).setPreferredWidth(450); // Value
        resultTable.getColumnModel().getColumn(4).setPreferredWidth(150); // Enum Description

        add(new JBScrollPane(resultTable), BorderLayout.CENTER);
    }

    private void performFindNext() {
        if (resultTable == null || searchField == null) {
            return;
        }
        String query = searchField.getText();
        if (query == null || query.trim().isEmpty()) {
            searchField.setForeground(UIManager.getColor("TextField.foreground"));
            return;
        }
        String textToFind = query.trim().toLowerCase();

        int rowCount = resultTable.getRowCount();
        int colCount = resultTable.getColumnCount();
        if (rowCount == 0 || colCount == 0) {
            return;
        }

        if (!textToFind.equals(lastSearchText)) {
            lastFoundRow = -1;
            lastFoundCol = -1;
            lastSearchText = textToFind;
        }

        int startRow = Math.max(lastFoundRow, 0);
        int startCol = lastFoundCol < 0 ? -1 : lastFoundCol;

        int currentCell = startRow * colCount + startCol + 1;
        int totalCells = rowCount * colCount;

        boolean found = false;
        for (int i = 0; i < totalCells; i++) {
            int cellIndex = (currentCell + i) % totalCells;
            int r = cellIndex / colCount;
            int c = cellIndex % colCount;

            Object val = resultTable.getValueAt(r, c);
            String valStr = val != null ? val.toString() : "";
            if (valStr.toLowerCase().contains(textToFind)) {
                lastFoundRow = r;
                lastFoundCol = c;

                searchField.setForeground(UIManager.getColor("TextField.foreground"));

                // Select cell and scroll to view
                resultTable.changeSelection(r, c, false, false);
                Rectangle cellRect = resultTable.getCellRect(r, c, true);
                if (cellRect != null) {
                    resultTable.scrollRectToVisible(cellRect);
                }
                resultTable.repaint();
                found = true;
                break;
            }
        }

        if (!found) {
            searchField.setForeground(JBColor.RED);
        }
    }

    private void updateDictionaryStatus() {
        if (dictionaryStatusLabel == null) return;
        String selected = (String) dictionaryCombo.getSelectedItem();
        if (selected != null && selected.endsWith(" (external)")) {
            dictionaryStatusLabel.setText("Custom dictionary active");
            dictionaryStatusLabel.setToolTipText("Using external dictionary definition");
        } else {
            dictionaryStatusLabel.setText("");
            dictionaryStatusLabel.setToolTipText(null);
        }
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
        updateDictionaryStatus();
    }

    private void browseExternalDictionary() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
        descriptor.withFileFilter(vf -> "xml".equalsIgnoreCase(vf.getExtension()));

        VirtualFile[] files = FileChooserFactory.getInstance().createFileChooser(descriptor, project, this)
                .choose(project);

        if (files.length > 0) {
            File file = new File(files[0].getPath());
            String displayName = file.getName() + " (external)";
            if (dictionaryService.loadExternal(file)) {
                refreshDictionaryCombo();
                dictionaryCombo.setSelectedItem(displayName);
                performParse();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to load dictionary from " + file.getName(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void rebuildMessageFromTable() {
        List<TagValuePair> pairs = tableModel.getData();
        String delimiter = getSelectedDelimiter();
        if (pairs.isEmpty() || delimiter == null || delimiter.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (TagValuePair pair : pairs) {
            sb.append(pair.tag()).append("=").append(pair.value()).append(delimiter);
        }

        isUpdatingUi = true;
        try {
            messageInput.setText(sb.toString());
        } finally {
            isUpdatingUi = false;
        }
    }

    public void performParse() {
        if (isUpdatingUi) return;
        if (parseTimer.isRunning()) {
            parseTimer.stop();
        }

        String message = messageInput.getText();
        if (message == null || message.trim().isEmpty()) {
            lastDetectedVersion = null;
            lastDetectedDelimiter = null;
            // Clear table if input is empty
            ApplicationManager.getApplication().invokeLater(() -> tableModel.setData(List.of(), Map.of()));
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
                lastSearchText = "";
                lastFoundRow = -1;
                lastFoundCol = -1;
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
            if (!detectedDelimiter.equals(lastDetectedDelimiter)) {
                updateDelimiterSelection(detectedDelimiter);
                lastDetectedDelimiter = detectedDelimiter;
            }
            if (fixVersion != null && !fixVersion.equals(lastDetectedVersion)) {
                String dictToSelect = mapFixVersionToDict(fixVersion);
                if (dictToSelect != null) {
                    dictionaryCombo.setSelectedItem(dictToSelect);
                }
                lastDetectedVersion = fixVersion;
            }
        } finally {
            isUpdatingUi = false;
        }
    }

    private String identifyDelimiter(String message) {
        // Find delimiter: check what's between BeginString and BodyLength
        // Standard pattern: 8=FIX.X.Y<DELIM>9=
        Pattern pattern = Pattern.compile("^8=FIX\\.[0-9a-zA-Z.]+(\\D)9=");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Fallback: check most common delimiters
        for (String delim : List.of(SOH, PIPE, CARET, TILDE)) {
            if (message.contains(delim)) return delim;
        }
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
        if (delimiter == null) return;
        switch (delimiter) {
            case PIPE -> pipeRadio.setSelected(true);
            case CARET -> caretRadio.setSelected(true);
            case TILDE -> tildeRadio.setSelected(true);
            case SOH -> sohRadio.setSelected(true);
            default -> {
                customRadio.setSelected(true);
                customSeparatorField.setText(delimiter);
            }
        }
    }

    private String mapFixVersionToDict(String version) {
        if (version == null) return null;
        String clean = version.replace(".", "");
        // Check more specific SP versions before generic FIX50
        for (String dict : List.of("FIX50SP1", "FIX50SP2", "FIX50", "FIX40", "FIX41", "FIX42", "FIX43", "FIX44", "FIXT11")) {
            if (clean.startsWith(dict)) return dict;
        }
        return null;
    }

    private String getSelectedDelimiter() {
        if (customRadio.isSelected()) return customSeparatorField.getText();
        if (caretRadio.isSelected()) return CARET;
        if (tildeRadio.isSelected()) return TILDE;
        if (sohRadio.isSelected()) return SOH;
        return PIPE;
    }

    private void performRecalculate() {
        List<TagValuePair> currentPairs = tableModel.getData();
        if (currentPairs == null || currentPairs.isEmpty()) {
            return;
        }

        // Find index of Tag 9 (BodyLength) and Tag 10 (CheckSum)
        int tag9Index = -1;
        int tag10Index = -1;
        for (int i = 0; i < currentPairs.size(); i++) {
            switch (currentPairs.get(i).tag()) {
                case 9 -> tag9Index = i;
                case 10 -> tag10Index = i;
            }
        }

        String delimiter = getSelectedDelimiter();
        if (delimiter == null || delimiter.isEmpty()) {
            return;
        }

        // 1. Recalculate BodyLength (Tag 9)
        int startIndex = tag9Index != -1 ? tag9Index + 1 : 0;
        int endIndex = tag10Index != -1 ? tag10Index : currentPairs.size();

        int bodyLength = 0;
        for (int i = startIndex; i < endIndex; i++) {
            TagValuePair pair = currentPairs.get(i);
            bodyLength += String.valueOf(pair.tag()).length() + 1 + pair.value().length() + delimiter.length();
        }

        List<TagValuePair> newPairs = new ArrayList<>(currentPairs);

        if (tag9Index != -1) {
            newPairs.set(tag9Index, new TagValuePair(9, String.valueOf(bodyLength)));
        }

        // 2. Recalculate CheckSum (Tag 10)
        int sum = 0;
        int checkSumLimit = tag10Index != -1 ? tag10Index : newPairs.size();
        for (int i = 0; i < checkSumLimit; i++) {
            TagValuePair pair = newPairs.get(i);
            String fieldString = pair.tag() + "=" + pair.value() + delimiter;
            for (int j = 0; j < fieldString.length(); j++) {
                sum += fieldString.charAt(j);
            }
        }
        int checksumVal = sum % 256;
        String checksumStr = String.format("%03d", checksumVal);

        if (tag10Index != -1) {
            newPairs.set(tag10Index, new TagValuePair(10, checksumStr));
        }

        String dictName = (String) dictionaryCombo.getSelectedItem();
        Map<Integer, FixFieldDescriptor> dict = dictName != null
                ? dictionaryService.getDictionary(dictName)
                : null;

        tableModel.setData(newPairs, dict);
        rebuildMessageFromTable();
    }
}
