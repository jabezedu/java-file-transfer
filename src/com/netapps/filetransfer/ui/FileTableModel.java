package com.netapps.filetransfer.ui;

import com.netapps.filetransfer.common.FileInfo;
import com.netapps.filetransfer.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 * Table model presenting remote file metadata (Name, Size, Modified Date).
 */
public class FileTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {"File Name", "Size", "Last Modified"};

    private final List<FileInfo> files = new ArrayList<>();

    /**
     * Constructs an empty FileTableModel.
     */
    public FileTableModel() {
    }

    /**
     * Replaces the model's backing list with new file entries.
     *
     * @param newFiles list of FileInfo objects
     */
    public void setFiles(List<FileInfo> newFiles) {
        files.clear();
        if (newFiles != null) {
            files.addAll(newFiles);
        }
        fireTableDataChanged();
    }

    /**
     * Retrieves the FileInfo object corresponding to a table row index.
     *
     * @param rowIndex selected row index
     * @return FileInfo instance or null if out of bounds
     */
    public FileInfo getFileAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < files.size()) {
            return files.get(rowIndex);
        }
        return null;
    }

    @Override
    public int getRowCount() {
        return files.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        FileInfo file = files.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return file.getName();
            case 1:
                return FormatUtil.formatByteSize(file.getSize());
            case 2:
                return FormatUtil.formatDate(file.getLastModified());
            default:
                return "";
        }
    }
}
