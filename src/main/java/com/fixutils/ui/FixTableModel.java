package com.fixutils.ui;

import com.fixutils.dictionary.FixFieldDescriptor;
import com.fixutils.parser.TagValuePair;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FixTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Tag", "Field Name", "Value", "Enum Description"};

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
        return columnIndex == 2; // Only the Value column is editable
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 2 && aValue instanceof String newValue) {
            TagValuePair pair = data.get(rowIndex);
            data.set(rowIndex, new TagValuePair(pair.tag(), newValue));
            fireTableCellUpdated(rowIndex, columnIndex);
            fireTableCellUpdated(rowIndex, 3); // Update enum description cell

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
            case 2 -> pair.value();
            case 3 -> {
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
}
