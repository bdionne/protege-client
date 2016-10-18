package org.protege.editor.owl.client.action;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ScheduledFuture;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import org.protege.editor.owl.client.LocalHttpClient;
import org.protege.editor.owl.client.api.exception.SynchronizationException;
import org.protege.editor.owl.client.event.ClientSessionChangeEvent;
import org.protege.editor.owl.client.event.ClientSessionChangeEvent.EventCategory;
import org.protege.editor.owl.client.event.ClientSessionListener;
import org.protege.editor.owl.client.util.ClientUtils;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.editor.owl.model.OWLModelManagerImpl;
import org.protege.editor.owl.server.versioning.ChangeHistoryUtils;
import org.protege.editor.owl.server.versioning.CollectingChangeVisitor;
import org.protege.editor.owl.server.versioning.api.ChangeHistory;
import org.protege.editor.owl.server.versioning.api.DocumentRevision;
import org.protege.editor.owl.server.versioning.api.VersionedOWLOntology;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.AnnotationChange;
import org.semanticweb.owlapi.model.ImportChange;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLAxiomChange;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration.MissingOntologyHeaderStrategy;
import org.semanticweb.owlapi.model.UnloadableImportException;

/**
 * @author Josef Hardi <johardi@stanford.edu> <br>
 * @author Timothy Redmond <tredmond@stanford.edu> <br>
 * Stanford Center for Biomedical Informatics Research
 */
public class EnableAutoUpdateAction extends AbstractClientAction implements ClientSessionListener {

    private static final long serialVersionUID = 1098490684799516207L;

    private Optional<VersionedOWLOntology> activeVersionOntology = Optional.empty();

    private ScheduledFuture<?> autoUpdate;
    private JCheckBoxMenuItem checkBoxMenuItem;

    @Override
    public void initialise() throws Exception {
        super.initialise();
        setEnabled(false); // initially the menu item is disabled
        getClientSession().addListener(this);
    }

    @Override
    public void dispose() throws Exception {
        super.dispose();
    }

    @Override
    public void handleChange(ClientSessionChangeEvent event) {
        if (event.hasCategory(EventCategory.SWITCH_ONTOLOGY)) {
            activeVersionOntology = Optional.ofNullable(event.getSource().getActiveVersionOntology());
            setEnabled(activeVersionOntology.isPresent());
        }
    }

    public void setMenuItem(JMenuItem menu) {
        checkBoxMenuItem = (JCheckBoxMenuItem) menu;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        killAutoUpdate();
        if (checkBoxMenuItem.isSelected()) {
            final VersionedOWLOntology vont = activeVersionOntology.get();
            autoUpdate = submitPeriodic(new AutoUpdate(getOWLModelManager(), vont), 15); // TODO Make the auto-update timing adjustable
        }
    }

    private void killAutoUpdate() {
        if (autoUpdate != null) {
            autoUpdate.cancel(false);
            autoUpdate = null;
        }
    }
    

    private class AutoUpdate implements Runnable {

    	private VersionedOWLOntology vont;
    	private OWLOntology ontology;
    	
    	private OWLModelManagerImpl modMan;

    	public AutoUpdate(OWLModelManager modMan, VersionedOWLOntology vont) {
    		this.vont = vont;
    		ontology = vont.getOntology();
    		this.modMan = (OWLModelManagerImpl) modMan;
    	}

    	@Override
    	public void run() {
    		try {
    			if (!isUpdated()) {
    				List<OWLOntologyChange> localChanges = getLatestChangesFromClient();
    				//List<OWLOntologyChange> remoteChanges = getLatestChangesFromServer();
    				ChangeHistory remoteChangeHistory = getLatestChangesFromServer();
                    List<OWLOntologyChange> remoteChanges = ChangeHistoryUtils.getOntologyChanges(remoteChangeHistory, ontology);
    				List<OWLOntologyChange> conflictChanges = getConflicts(localChanges, remoteChanges);
    				if (conflictChanges.isEmpty()) {
    					performUpdate(remoteChanges);
    					vont.update(remoteChangeHistory);
    				}
    				else {
    					throw new SynchronizationException("Conflict was detected and unable to merge changes from the server");
    				}
    			}
    		}

    		catch (SynchronizationException udae) {
    			autoUpdate.cancel(false);
    			autoUpdate = null;
    			checkBoxMenuItem.setSelected(false);
    			showErrorDialog("AutoUpdate error:", udae.getMessage(), udae);
    		}
    		catch (Throwable t) {
    			// TODO: should set something false here
    		}

    	}

        

        private void performUpdate(List<OWLOntologyChange> updates) {
        	getSessionRecorder().stopRecording();       	
            SwingUtilities.invokeLater(() -> ontology.getOWLOntologyManager().applyChanges(updates));
            getOWLEditorKit().getSearchManager().updateIndex(updates);
            getSessionRecorder().startRecording();
        	
            adjustImports(updates);
        }

        private boolean isUpdated() {
            try {
                DocumentRevision remoteHead = LocalHttpClient.current_user().getRemoteHeadRevision(vont);
                DocumentRevision localHead = vont.getHeadRevision();
                return localHead.sameAs(remoteHead);
            }
            catch (Exception e) {
                showErrorDialog("Update error", "Error while fetching the remote head revision", e);
                return false;
            }
        }

        public List<OWLOntologyChange> getLatestChangesFromClient() {
            return ClientUtils.getUncommittedChanges(getSessionRecorder(), vont.getOntology(), vont.getChangeHistory());
        }

        private ChangeHistory getLatestChangesFromServer() {
            //List<OWLOntologyChange> changes = new ArrayList<>();
        	ChangeHistory remoteChangeHistory = null;
            try {
                remoteChangeHistory = LocalHttpClient.current_user().getLatestChanges(vont);
                //changes = ChangeHistoryUtils.getOntologyChanges(remoteChangeHistory, ontology);
            }
            catch (Exception e) {
                showErrorDialog("Update error", "Error while fetching the latest changes from server", e);
            }
            return remoteChangeHistory;
        }

        private List<OWLOntologyChange> getConflicts(List<OWLOntologyChange> localChanges, List<OWLOntologyChange> remoteChanges) {
            List<OWLOntologyChange> conflictChanges = new ArrayList<>();

            CollectingChangeVisitor clientChanges = CollectingChangeVisitor.collectChanges(localChanges);
            CollectingChangeVisitor serverChanges = CollectingChangeVisitor.collectChanges(remoteChanges);

            /*
             * Compute the conflicts by comparing the change signature between the client and server changes. The
             * change signature is defined by the OWL object that becomes the focus of the change (i.e.,
             * OWLImportDeclaration, OWLAnnotation, OWLAxiom) or the ontology ID.
             */
            if (clientChanges.getLastOntologyIDChange() != null && serverChanges.getLastOntologyIDChange() != null) {
                conflictChanges.add(clientChanges.getLastOntologyIDChange());
            }
            final Map<OWLImportsDeclaration, ImportChange> importChanges = clientChanges.getLastImportChangeMap();
            for (Entry<OWLImportsDeclaration, ImportChange> entry : importChanges.entrySet()) {
                OWLImportsDeclaration decl = entry.getKey();
                if (serverChanges.getLastImportChangeMap().containsKey(decl)) {
                    conflictChanges.add(entry.getValue());
                }
            }
            final Map<OWLAnnotation, AnnotationChange> annotationChanges = clientChanges.getLastOntologyAnnotationChangeMap();
            for (Entry<OWLAnnotation, AnnotationChange> entry : annotationChanges.entrySet()) {
                OWLAnnotation annotation = entry.getKey();
                if (serverChanges.getLastOntologyAnnotationChangeMap().containsKey(annotation)) {
                    conflictChanges.add(entry.getValue());
                }
            }
            final Map<OWLAxiom, OWLAxiomChange> axiomChanges = clientChanges.getLastAxiomChangeMap();
            for (Entry<OWLAxiom, OWLAxiomChange> entry : axiomChanges.entrySet()) {
                OWLAxiom axiom = entry.getKey();
                if (serverChanges.getLastAxiomChangeMap().containsKey(axiom)) {
                    conflictChanges.add(entry.getValue());
                }
            }
            return conflictChanges;
        }

        private void adjustImports(List<OWLOntologyChange> changes) {
            OWLOntologyLoaderConfiguration configuration = new OWLOntologyLoaderConfiguration();
            configuration = configuration.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
            configuration = configuration.setMissingOntologyHeaderStrategy(MissingOntologyHeaderStrategy.IMPORT_GRAPH);
            try {
                adjustImports(changes, configuration);
            }
            catch (UnloadableImportException e) {
                showErrorDialog("Update error", "Unexpected error when adjusting import declarations", e);
            }
        }
        
        private void adjustImports(List<OWLOntologyChange> changes, OWLOntologyLoaderConfiguration configuration)
                throws UnloadableImportException {
            Set<OWLImportsDeclaration> finalDeclaredImports = ontology.getImportsDeclarations();
            Set<OWLImportsDeclaration> missingImports = new TreeSet<OWLImportsDeclaration>();
            for (OWLOntologyChange change : changes) {
                if (change instanceof AddImport) {
                    OWLImportsDeclaration importDecl = ((AddImport) change).getImportDeclaration();
                    if (finalDeclaredImports.contains(importDecl)
                            && ontology.getOWLOntologyManager().getImportedOntology(importDecl) == null) {
                        missingImports.add(importDecl);
                    }
                }
            }
            for (OWLImportsDeclaration missingImport : missingImports) {
                ontology.getOWLOntologyManager().makeLoadImportRequest(missingImport, configuration);
            }
        }

		
    }

    
}
