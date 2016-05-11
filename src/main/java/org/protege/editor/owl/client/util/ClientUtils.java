package org.protege.editor.owl.client.util;

import org.protege.editor.owl.server.versioning.ServerDocument;
import org.protege.editor.owl.server.versioning.VersionedOWLOntologyImpl;
import org.protege.editor.owl.server.versioning.api.ChangeHistory;
import org.protege.editor.owl.server.versioning.api.VersionedOWLOntology;

import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLMutableOntology;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration.MissingOntologyHeaderStrategy;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ClientUtils {

    public static OWLOntology buildOntology(ServerDocument serverDocument, OWLMutableOntology targetOntology) {
        if (serverDocument.getChangeHistory().isPresent()) {
            ChangeHistory remoteChangeHistory = serverDocument.getChangeHistory().get();
            List<OWLOntologyChange> changes = remoteChangeHistory.getChanges(targetOntology);
            targetOntology.applyChanges(changes);
            fixMissingImports(targetOntology, changes);
        }
        return targetOntology;
    }

    public static VersionedOWLOntology constructVersionedOntology(ServerDocument serverDocument, OWLOntology targetOntology) {
        VersionedOWLOntology versionedOntology = new VersionedOWLOntologyImpl(serverDocument, targetOntology);
        if (serverDocument.getChangeHistory().isPresent()) {
            versionedOntology.appendChangeHistory(serverDocument.getChangeHistory().get());
        }
        return versionedOntology;
    }

    private static void fixMissingImports(OWLMutableOntology targetOntology, List<OWLOntologyChange> changes) {
        OWLOntologyManager manager = targetOntology.getOWLOntologyManager();
        OWLOntologyLoaderConfiguration configuration = new OWLOntologyLoaderConfiguration();
        configuration = configuration.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
        configuration = configuration.setMissingOntologyHeaderStrategy(MissingOntologyHeaderStrategy.IMPORT_GRAPH);
        
        final Set<OWLImportsDeclaration> declaredImports = targetOntology.getImportsDeclarations();
        Set<OWLImportsDeclaration> missingImports = new TreeSet<OWLImportsDeclaration>();
        for (OWLOntologyChange change : changes) {
            if (change instanceof AddImport) {
                OWLImportsDeclaration importDecl = ((AddImport) change).getImportDeclaration();
                if (declaredImports.contains(importDecl) && manager.getImportedOntology(importDecl) == null) {
                    missingImports.add(importDecl);
                }
            }
        }
        for (OWLImportsDeclaration importDecl : missingImports) {
            manager.makeLoadImportRequest(importDecl, configuration);
        }
    }
}