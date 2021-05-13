package at.ac.tuwien.kr.hexlite;

import java.util.List;

import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

interface IOntologyContext {
   public OWLDataFactory df();
   public OWLOntologyManager manager();
   public OWLReasoner reasoner();
   public OWLOntology ontology();
   public String expandNamespace(String value);
   public String simplifyNamespaceIfPossible(String value);
   public void applyChanges(List<? extends OWLOntologyChange> changes);
   public void revertChanges(List<? extends OWLOntologyChange> changes);
   public void teardown();
}

