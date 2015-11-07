package org.protege.editor.owl.client.diff.ui;

import org.protege.editor.owl.client.diff.model.*;
import org.protege.owl.server.api.UserId;
import org.semanticweb.owlapi.model.OWLObject;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Rafael Gonçalves <br>
 * Stanford Center for Biomedical Informatics Research
 */
public class ChangesTableModel extends AbstractTableModel {
    private static final long serialVersionUID = -1584544532122408048L;
    private List<Change> changes = new ArrayList<>();

    /**
     * No-args constructor
     */
    public ChangesTableModel() { }

    public void setChanges(List<Change> changes) {
        this.changes = new ArrayList<>(checkNotNull(changes));
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return (changes != null ? changes.size() : 0);
    }

    @Override
    public int getColumnCount() {
        return Column.values().length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Change change = getChange(rowIndex);
        switch (Column.values()[columnIndex]) {
            case MODE:
                return change.getChangeMode();
            case DATE:
                return change.getDate();
            case AUTHOR:
                return change.getAuthor();
            case CHANGE_SUBJECT:
                return change.getSubject();
            case CHANGE_TYPE:
                return change.getType();
            case COMMENT:
                return change.getCommitComment();
            case CONFLICT:
                return change.isConflicting();
            case REVIEW:
                return change.getReview();
            default:
                throw new IllegalStateException();
        }
    }

    public Change getChange(int rowIndex) {
        return changes.get(rowIndex);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        Column col = Column.values()[columnIndex];
        switch (col) {
            case DATE:
                return Date.class;
            case AUTHOR:
                return UserId.class;
            case MODE:
                return ChangeMode.class;
            case COMMENT:
                return String.class;
            case CHANGE_TYPE:
                return ChangeType.class;
            case CHANGE_SUBJECT:
                return OWLObject.class;
            case CONFLICT:
                return Boolean.class;
            case REVIEW:
                return ChangeReview.class;
            default:
                throw new IllegalStateException("Programmer Error: a case was missed");
        }
    }

    public String getColumnName(int column) {
        return Column.values()[column].toString();
    }

    public void clear() {
        changes.clear();
    }

    public enum Column {
        MODE("Mode"),
        DATE("Date"),
        AUTHOR("Author"),
        CHANGE_SUBJECT("Change Subject"),
        CHANGE_TYPE("Type"),
        COMMENT("Comment"),
        CONFLICT("Conflict"),
        REVIEW("Review");

        private String name;

        Column(String name) {
            this.name = checkNotNull(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
