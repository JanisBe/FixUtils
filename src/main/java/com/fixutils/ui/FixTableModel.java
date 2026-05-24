package com.fixutils.ui;

import com.fixutils.dictionary.FixFieldDescriptor;
import com.fixutils.parser.TagValuePair;
import com.fixutils.validation.FixTypeValidator;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FixTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Tag", "Field Name", "Type", "Value", "Enum Description"};

    private List<TagValuePair> data = new ArrayList<>();
    private Map<Integer, FixFieldDescriptor> currentDictionary = Collections.emptyMap();

    public interface ValueUpdateListener {
        void onValueUpdated();
    }

    private ValueUpdateListener updateListener;

    public void setValueUpdateListener(ValueUpdateListener listener) {
        this.updateListener = listener;
    }

    public void setData(List<TagValuePair> pairs, Map<Integer, FixFieldDescriptor> dict) {
        this.data = pairs != null ? new ArrayList<>(pairs) : new ArrayList<>();
        this.currentDictionary = dict != null ? dict : Collections.emptyMap();
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return data.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 3; // Only the Value column is editable (now index 3)
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 3 && aValue instanceof String newValue) {
            TagValuePair pair = data.get(rowIndex);
            data.set(rowIndex, new TagValuePair(pair.tag(), newValue));
            fireTableCellUpdated(rowIndex, columnIndex);
            fireTableCellUpdated(rowIndex, 4); // Update enum description cell (now index 4)

            if (updateListener != null) {
                updateListener.onValueUpdated();
            }
        }
    }

    public List<TagValuePair> getData() {
        return data;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        TagValuePair pair = data.get(rowIndex);
        int tag = pair.tag();
        FixFieldDescriptor desc = currentDictionary.get(tag);

        return switch (columnIndex) {
            case 0 -> String.valueOf(tag);
            case 1 -> desc != null ? desc.name() : "[unknown]";
            case 2 -> desc != null ? desc.type() : "";
            case 3 -> pair.value();
            case 4 -> {
                if (desc != null) {
                    yield desc.enumValues().getOrDefault(pair.value(), "");
                }
                yield "";
            }
            default -> null;
        };
    }

    public boolean isUnknownTag(int rowIndex) {
        TagValuePair pair = data.get(rowIndex);
        return !currentDictionary.containsKey(pair.tag());
    }

    public boolean isValueValid(int rowIndex) {
        TagValuePair pair = data.get(rowIndex);
        FixFieldDescriptor desc = currentDictionary.get(pair.tag());
        if (desc == null || desc.type() == null) {
            return true;
        }
        return FixTypeValidator.isValid(pair.value(), desc.type());
    }

    public String getValidationErrorMessage(int rowIndex) {
        TagValuePair pair = data.get(rowIndex);
        FixFieldDescriptor desc = currentDictionary.get(pair.tag());
        if (desc == null || desc.type() == null) {
            return null;
        }
        if (!FixTypeValidator.isValid(pair.value(), desc.type())) {
            String help = FixTypeValidator.getExpectedFormatHelp(desc.type());
            return "Value '" + pair.value() + "' does not match expected type " + desc.type() +
                   (!help.isEmpty() ? " (" + help + ")" : "");
        }
        return null;
    }
}
