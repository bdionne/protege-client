package org.protege.editor.owl.client.action;

import java.awt.event.ActionEvent;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.swing.JOptionPane;
import javax.swing.JTextArea;

import org.protege.editor.owl.client.api.Client;
import org.protege.editor.owl.client.api.exception.ClientRequestException;
import org.protege.editor.owl.client.api.exception.SynchronizationException;
import org.protege.editor.owl.client.util.ClientUtils;
import org.protege.editor.owl.model.event.OWLModelManagerChangeEvent;
import org.protege.editor.owl.model.event.OWLModelManagerListener;
import org.protege.editor.owl.server.api.CommitBundle;
import org.protege.editor.owl.server.api.exception.AuthorizationException;
import org.protege.editor.owl.server.api.exception.OutOfSyncException;
import org.protege.editor.owl.server.policy.CommitBundleImpl;
import org.protege.editor.owl.server.versioning.Commit;
import org.protege.editor.owl.server.versioning.api.ChangeHistory;
import org.protege.editor.owl.server.versioning.api.DocumentRevision;
import org.protege.editor.owl.server.versioning.api.VersionedOWLOntology;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyChangeListener;

public class CommitAction extends AbstractClientAction {

    private static final long serialVersionUID = 4601012273632698091L;

    private List<OWLOntologyChange> localChanges = new ArrayList<>();

    private OWLOntologyChangeListener checkUncommittedChanges = new OWLOntologyChangeListener() {
        @Override
        public void ontologiesChanged(List<? extends OWLOntologyChange> changes) throws OWLException {
            optionEnabled();
        }
    };

    private OWLModelManagerListener checkOutstandingChanges = new OWLModelManagerListener() {
        @Override
        public void handleChange(OWLModelManagerChangeEvent event) {
            optionEnabled();
        }
    };

    @Override
    public void initialise() throws Exception {
        super.initialise();
        setEnabled(false);
        getOWLModelManager().addOntologyChangeListener(checkUncommittedChanges);
        getOWLModelManager().addListener(checkOutstandingChanges);
    }

    @Override
    public void dispose() throws Exception {
        super.dispose();
        getOWLModelManager().removeOntologyChangeListener(checkUncommittedChanges);
        getOWLModelManager().removeListener(checkOutstandingChanges);
    }

    private void optionEnabled() {
        OWLOntology activeOntology = getOWLEditorKit().getOWLModelManager().getActiveOntology();
        Optional<VersionedOWLOntology> vont = getOntologyResource();
        if (vont.isPresent()) {
            ChangeHistory baseline = vont.get().getChangeHistory();
            localChanges = ClientUtils.getUncommittedChanges(activeOntology, baseline);
            setEnabled(!localChanges.isEmpty());
        }
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
        try {
            String comment = "";
            VersionedOWLOntology vont = getActiveVersionOntology();
            while (true) {
                JTextArea commentArea = new JTextArea(4, 45);
                Object[] message = { "Commit message (do not leave blank):", commentArea };
                int option = JOptionPane.showConfirmDialog(null, message, "Commit",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (option == JOptionPane.CANCEL_OPTION) {
                    break;
                }
                else if (option == JOptionPane.OK_OPTION) {
                    comment = commentArea.getText().trim();
                    if (!comment.isEmpty()) {
                        break;
                    }
                }
            }
            performCommit(vont, comment);
        }
        catch (SynchronizationException e) {
            showErrorDialog("Synchronization error", e.getMessage(), e);
        }
    }

    private void performCommit(VersionedOWLOntology vont, String comment) {
        try {
            Optional<ChangeHistory> acceptedChanges = commit(vont.getHeadRevision(), localChanges, comment);
            if (acceptedChanges.isPresent()) {
                ChangeHistory changes = acceptedChanges.get();
                vont.update(changes); // update the local ontology
                setEnabled(false); // disable the commit action after the changes got committed successfully
                showInfoDialog("Commit", "Commit success (uploaded as revision " + changes.getHeadRevision() + ")");
            }
        }
        catch (InterruptedException e) {
            showErrorDialog("Commit error", "Internal error: " + e.getMessage(), e);
        }
    }

    private Optional<ChangeHistory> commit(DocumentRevision revision, List<OWLOntologyChange> localChanges, String comment) throws InterruptedException {
        Optional<ChangeHistory> acceptedChanges = Optional.empty();
        try {
            Future<?> task = submit(new DoCommit(revision, getClient(), comment, localChanges));
            acceptedChanges = Optional.ofNullable((ChangeHistory) task.get());
        }
        catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof AuthorizationException) {
                showErrorDialog("Authorization error", t.getMessage(), t);
            }
            else if (t instanceof OutOfSyncException) {
                showErrorDialog("Synchronization error", t.getMessage(), t);
            }
            else if (t instanceof ClientRequestException) {
                showErrorDialog("Commit error", t.getMessage(), t);
            }
            else if (t instanceof RemoteException) {
                showErrorDialog("Network error", t.getMessage(), t);
            }
            else {
                showErrorDialog("Commit error", t.getMessage(), t);
            }
        }
        return acceptedChanges;
    }

    private class DoCommit implements Callable<ChangeHistory> {

        private DocumentRevision commitBaseRevision;
        private Client author;
        private String comment;
        private List<OWLOntologyChange> changes;

        public DoCommit(DocumentRevision commitBaseRevision, Client author, String comment,
                List<OWLOntologyChange> changes) {
            this.commitBaseRevision = commitBaseRevision;
            this.author = author;
            this.comment = comment;
            this.changes = changes;
        }

        @Override
        public ChangeHistory call() throws AuthorizationException, OutOfSyncException,
                ClientRequestException, RemoteException {
            Commit commit = ClientUtils.createCommit(author, comment, changes);
            CommitBundle commitBundle = new CommitBundleImpl(commitBaseRevision, commit);
            return author.commit(getClientSession().getActiveProject(), commitBundle);
        }
    }
}
