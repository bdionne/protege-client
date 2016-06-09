package org.protege.editor.owl.client.admin.ui;

import com.google.common.base.Objects;
import edu.stanford.protege.metaproject.api.Project;
import org.protege.editor.core.Disposable;
import org.protege.editor.core.ui.error.ErrorLogPanel;
import org.protege.editor.core.ui.list.MList;
import org.protege.editor.core.ui.list.MListSectionHeader;
import org.protege.editor.core.ui.util.JOptionPaneEx;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.client.ClientSession;
import org.protege.editor.owl.client.admin.AdminTabManager;
import org.protege.editor.owl.client.admin.model.AdminTabEvent;
import org.protege.editor.owl.client.admin.model.AdminTabListener;
import org.protege.editor.owl.client.admin.model.ProjectMListItem;
import org.protege.editor.owl.client.api.Client;
import org.protege.editor.owl.client.api.exception.ClientRequestException;
import org.protege.editor.owl.server.api.exception.AuthorizationException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Rafael Gonçalves <br>
 * Stanford Center for Biomedical Informatics Research
 */
public class ProjectPanel extends JPanel implements Disposable {
    private static final long serialVersionUID = 5919830988663977652L;
    private OWLEditorKit editorKit;
    private AdminTabManager configManager;
    private MList projectList;
    private Project selectedProject;

    /**
     * Constructor
     *
     * @param editorKit    OWL editor kit
     */
    public ProjectPanel(OWLEditorKit editorKit) {
        this.editorKit = checkNotNull(editorKit);
        configManager = AdminTabManager.get(editorKit);
        configManager.addListener(tabListener);
        initUiComponents();
    }

    private AdminTabListener tabListener = event -> {
        if (event.equals(AdminTabEvent.SELECTION_CHANGED)) {
            if(configManager.hasSelection() && !configManager.getSelection().isProject()) {
                projectList.clearSelection();
            }
        }
    };

    private void initUiComponents() {
        setupList();
        setLayout(new BorderLayout());
        JScrollPane scrollpane = new JScrollPane(projectList);
        scrollpane.setBorder(new EmptyBorder(3, 0, 0, 0));
        add(scrollpane, BorderLayout.CENTER);
        listProjects();
    }

    private ListSelectionListener listSelectionListener = e -> {
        Object selectedObj = projectList.getSelectedValue();
        if (selectedObj != null && !e.getValueIsAdjusting()) {
            if (selectedObj instanceof ProjectListItem) {
                selectedProject = ((ProjectListItem) selectedObj).getProject();
                configManager.setSelection(selectedProject);
            }
            else if (selectedObj instanceof ProjectListHeaderItem) {
                configManager.clearSelection();
            }
        }
    };

    private void setupList() {
        projectList = new MList() {
            protected void handleAdd() {
                addProject();
            }

            protected void handleDelete() {
                deleteProject();
            }

            protected void handleEdit() {
                editProject();
            }
        };
        projectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        projectList.addListSelectionListener(listSelectionListener);
        projectList.setCellRenderer(new ProjectListCellRenderer());
    }

    private void listProjects() {
        Client client = ClientSession.getInstance(editorKit).getActiveClient();
        ArrayList<Object> data = new ArrayList<>();
        data.add(new ProjectListHeaderItem());
        try {
            if(client != null) {
                List<Project> projects = client.getAllProjects();
                Collections.sort(projects);
                data.addAll(projects.stream().map(ProjectListItem::new).collect(Collectors.toList()));
            }
        } catch (AuthorizationException | ClientRequestException | RemoteException e) {
            ErrorLogPanel.showErrorDialog(e);
        }
        projectList.setListData(data.toArray());
    }

    private void addProject() {
        Optional<Project> project = ProjectDialogPanel.showDialog(editorKit);
        if(project.isPresent()) {
            configManager.statusChanged(AdminTabEvent.CONFIGURATION_CHANGED);
            listProjects();
            selectProject(project.get());
        }
    }

    private void editProject() {
        Optional<Project> project = ProjectDialogPanel.showDialog(editorKit, selectedProject);
        if(project.isPresent()) {
            configManager.statusChanged(AdminTabEvent.CONFIGURATION_CHANGED);
            listProjects();
            selectProject(project.get());
        }
    }

    private void deleteProject() {
        Object selectedObj = projectList.getSelectedValue();
        if (selectedObj instanceof ProjectListItem) {
            Project project = ((ProjectListItem) selectedObj).getProject();
            String projectName = project.getName().get();

            JPanel panel = new JPanel(new GridLayout(0, 1));
            panel.add(new JLabel("Proceed to delete project '" + projectName + "'? All policy entries involving '" + projectName + "' will be removed."));
            JCheckBox checkBox = new JCheckBox("Delete the history file of the project");
            panel.add(checkBox);

            int res = JOptionPaneEx.showConfirmDialog(editorKit.getWorkspace(), "Delete Project '" + projectName + "'", panel,
                    JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION, null);
            if (res != JOptionPane.OK_OPTION) {
                return;
            }
            Client client = ClientSession.getInstance(editorKit).getActiveClient();
            try {
                client.deleteProject(project.getId(), checkBox.isSelected());
            } catch (AuthorizationException | ClientRequestException | RemoteException e) {
                ErrorLogPanel.showErrorDialog(e);
            }
            configManager.statusChanged(AdminTabEvent.CONFIGURATION_CHANGED);
            listProjects();
        }
    }

    private void selectProject(Project project) {
        for(int i = 0; i < projectList.getModel().getSize(); i++) {
            Object item = projectList.getModel().getElementAt(i);
            if(item instanceof ProjectListItem) {
                if (((ProjectListItem)item).getProject().getId().equals(project.getId())) {
                    projectList.setSelectedValue(item, true);
                    break;
                }
            }
        }
    }

    /**
     * Add Project item
     */
    public class ProjectListHeaderItem implements MListSectionHeader {

        @Override
        public String getName() {
            return "Projects";
        }

        @Override
        public boolean canAdd() {
            return true;
        }
    }

    /**
     * Project list item
     */
    public class ProjectListItem implements ProjectMListItem {
        private Project project;

        /**
         * Constructor
         *
         * @param project   Project
         */
        public ProjectListItem(Project project) {
            this.project = checkNotNull(project);
        }

        @Override
        public Project getProject() {
            return project;
        }

        @Override
        public boolean isEditable() {
            return true;
        }

        @Override
        public void handleEdit() {

        }

        @Override
        public boolean isDeleteable() {
            return true;
        }

        @Override
        public boolean handleDelete() {
            return true;
        }

        @Override
        public String getTooltip() {
            return project.getName().get();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ProjectListItem)) {
                return false;
            }
            ProjectListItem that = (ProjectListItem) o;
            return Objects.equal(project, that.project);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(project);
        }
    }

    @Override
    public void dispose() {
        projectList.removeListSelectionListener(listSelectionListener);
        configManager.removeListener(tabListener);
    }
}