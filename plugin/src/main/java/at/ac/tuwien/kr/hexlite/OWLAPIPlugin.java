package at.ac.tuwien.kr.hexlite;

import java.io.File;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.util.AutoIRIMapper;

import at.ac.tuwien.kr.hexlite.api.Answer;
import at.ac.tuwien.kr.hexlite.api.ExtSourceProperties;
import at.ac.tuwien.kr.hexlite.api.IPlugin;
import at.ac.tuwien.kr.hexlite.api.IPluginAtom;
import at.ac.tuwien.kr.hexlite.api.ISolverContext;

interface IOntologyContext {
    public OWLDataFactory df();
    public OWLOntologyManager manager();
    public OWLReasoner reasoner();
    public OWLOntology ontology();
}

interface IPluginContext {
    public IOntologyContext ontologyContext(String ontolocation);
}

public class OWLAPIPlugin implements IPlugin, IPluginContext {
    private static final Logger LOGGER = LogManager.getLogger("Hexlite-OWLAPI-Plugin");

    private class OntologyContext implements IOntologyContext {
        String _uri;
        OWLDataFactory _df;
        OWLOntologyManager _manager;
        OWLOntology _ontology;
        OWLReasonerFactory _reasonerFactory;
        OWLReasoner _reasoner;

        public OntologyContext(String uri) {
            if( uri.indexOf("://") == -1 ) {
                if( uri.startsWith("/") ) {
                    _uri = "file://"+uri;
                } else {
                    _uri = "file://" + System.getProperty("user.dir") + "/" + uri;
                }
            } else {
                _uri = uri;
            }            
            _df = OWLManager.getOWLDataFactory();
            _manager = OWLManager.createOWLOntologyManager();
            
            // make dependency ontologies auto-loadable from current directory
            final File file = new File(System.getProperty("user.dir"));
            _manager.getIRIMappers().add(new AutoIRIMapper(file, true));

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

    public OWLAPIPlugin() {
        cachedContexts = new HashMap<String, IOntologyContext>();
    }

    @Override
    public IOntologyContext ontologyContext(String ontolocation) {
        if( !cachedContexts.containsKey(ontolocation) ) {
            cachedContexts.put(ontolocation, new OntologyContext(ontolocation));
        }
        return cachedContexts.get(ontolocation);
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

        private String withoutQuotes(String s) {
            if( s.startsWith("\"") && s.endsWith("\"") )
                return s.substring(1, s.length()-1);
            else
                return s;
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            System.err.println("in retrieve!");
            LOGGER.error("in retrieve!");
            String location = withoutQuotes(query.getInput().get(0).value());
            String conceptQuery = withoutQuotes(query.getInput().get(1).value());
            LOGGER.warn(getPredicate() + " retrieving with ontoURI="+location+" and query '"+conceptQuery+"'");
            IOntologyContext oc = ontologyContext(location);
            LOGGER.info("got context");

            final Answer answer = new Answer();
            LOGGER.info("creating answer");
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