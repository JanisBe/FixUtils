package com.fixutils.ui;

import com.fixutils.dictionary.FixFieldDescriptor;
import com.fixutils.parser.TagValuePair;

import javax.swing.table.AbstractTableModel;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FixTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Tag", "Field Name", "Value", "Enum Description"};

    private List<TagValuePair> data = Collections.emptyList();
    private Map<Integer, FixFieldDescriptor> currentDictionary = Collections.emptyMap();

    public void setData(List<TagValuePair> pairs, Map<Integer, FixFieldDescriptor> dict) {
        this.data = pairs != null ? pairs : Collections.emptyList();
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
