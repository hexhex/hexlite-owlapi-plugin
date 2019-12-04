package at.ac.tuwien.kr.hexlite;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedList;

import at.ac.tuwien.kr.hexlite.api.Answer;
import at.ac.tuwien.kr.hexlite.api.ExtSourceProperties;
import at.ac.tuwien.kr.hexlite.api.IPlugin;
import at.ac.tuwien.kr.hexlite.api.IPluginAtom;
import at.ac.tuwien.kr.hexlite.api.ISolverContext;
import at.ac.tuwien.kr.hexlite.api.ISymbol;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.util.AutoIRIMapper;

interface IOntologyContext {
    public OWLDataFactory df();
    public OWLOntologyManager manager();
    public OWLReasoner reasoner();
    public OWLOntology ontology();
}

interface IPluginContext {
    public IOntologyContext ontologyContext(String uri);
}

public class OWLAPIPlugin implements IPlugin, IPluginContext {

    private class OntologyContext implements IOntologyContext {
        String _uri;
        OWLDataFactory _df;
        OWLOntologyManager _manager;
        OWLOntology _ontology;
        OWLReasonerFactory _reasonerFactory;
        OWLReasoner _reasoner;

        public OntologyContext(String uri) {
            _uri = uri;
            _df = OWLManager.getOWLDataFactory();
            _manager = OWLManager.createOWLOntologyManager();
            try {
                _ontology = _manager.loadOntology(IRI.create(_uri));
            } catch(OWLOntologyCreationException e) {
                System.err.println("could not load ontology "+_uri+" with exception "+e.toString());
            }
            _reasonerFactory = new StructuralReasonerFactory();
            _reasoner = _reasonerFactory.createNonBufferingReasoner(_ontology);
            _reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        }

        public OWLDataFactory df() { return _df; }
        public OWLOntologyManager manager() { return _manager; }
        public OWLReasoner reasoner() { return _reasoner; }
        public OWLOntology ontology() { return _ontology; }
    }

    private AbstractMap<String, IOntologyContext> cachedContexts;

    @Override
    public IOntologyContext ontologyContext(String uri) {
        if( !cachedContexts.containsKey(uri) ) {
            cachedContexts.put(uri, new OntologyContext(uri));
        }
        return cachedContexts.get(uri);
    }

    public class DescriptionLogicConceptQueryAtom implements IPluginAtom {
        private final ArrayList<InputType> inputArguments;

        public DescriptionLogicConceptQueryAtom() {
            inputArguments = new ArrayList<InputType>();
            inputArguments.add(InputType.CONSTANT);
            inputArguments.add(InputType.CONSTANT);            
        }

        @Override
        public String getPredicate() {
            return "dlCro";
        }

        @Override
        public ArrayList<InputType> getInputArguments() {
            return inputArguments;
        }

        @Override
        public int getOutputArguments() {
            return 1;
        }

        @Override
        public ExtSourceProperties getExtSourceProperties() {
            return new ExtSourceProperties();
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            System.err.println("in retrieve!");
            String ontoURI = query.getInput().get(0).value();
            String conceptQuery = query.getInput().get(1).value();
            if( conceptQuery.startsWith("\"") && conceptQuery.endsWith("\"") )
                conceptQuery = conceptQuery.substring(1, conceptQuery.length()-1);
            System.err.println(getPredicate() + " retrieving with ontoURI="+ontoURI+" and query '"+conceptQuery+"'");
            IOntologyContext oc = ontologyContext(ontoURI);
            System.err.println("got context");

            final Answer answer = new Answer();
            System.err.println("creating answer");
            //final ArrayList<ISymbol> t = new ArrayList<ISymbol>(1);
            //t.add(ctx.storeConstant(b.toString()));
            //answer.output(t);
            return answer;
        }
    }

    @Override
    public String getName() {
        return "OWLAPIPlugin";
    }

    @Override
    public AbstractCollection<IPluginAtom> createAtoms() {
        final LinkedList<IPluginAtom> atoms = new LinkedList<IPluginAtom>();
        atoms.add(new DescriptionLogicConceptQueryAtom());
        return atoms;
	}
}