package org.protege.editor.owl.client.panel;

import java.util.Date;

import javax.swing.table.AbstractTableModel;

import org.protege.owl.server.api.ChangeDocument;
import org.protege.owl.server.api.ChangeMetaData;

public class HistoryTableModel extends AbstractTableModel {
    public enum Column {
        DATE("Date", Date.class) {
            @Override
            public Date getValue(ChangeMetaData metaData) {
                return metaData.getDate();
            } 
        },
        USER("Committer", String.class) {
            @Override
            public String getValue(ChangeMetaData metaData) {
                return metaData.getUsername();
            }             
        },
        COMMIT_COMMENT("Description", String.class) {
            @Override
            public String getValue(ChangeMetaData metaData) {
                return metaData.getCommitComment();
            } 
        };
        
        private String name;
        private Class<?> clazz;            @Override
        public Object getValue(ChangeMetaData metaData) {
            return metaData.getDate();
        } 
        
        private Column(String name, Class<?> clazz) {
            this.name = name;
            this.clazz = clazz;
        }
        
        public String getName() {
            return name;
        }
        
        public Class<?> getClazz() {
            return clazz;
        }
        
        public abstract Object getValue(ChangeMetaData metaData);
        
    }
    private ChangeDocument changes;

    @Override
    public int getRowCount() {
        return changes.getEndRevision().getRevision() - changes.getStartRevision().getRevision();
    }

    @Override
    public int getColumnCount() {
        return Column.values().length;
    }
    
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        Column col = Column.values()[columnIndex];
        return col.getClazz();
    }
    
    @Override
    public String getColumnName(int column) {
        Column col = Column.values()[column];
        return col.getName();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        // TODO Auto-generated method stub
        return null;
    }

}
