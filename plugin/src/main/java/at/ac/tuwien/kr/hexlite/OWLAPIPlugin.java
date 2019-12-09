package at.ac.tuwien.kr.hexlite;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.ParseException;
import org.json.simple.parser.JSONParser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;
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
import at.ac.tuwien.kr.hexlite.api.ISymbol;

interface IOntologyContext {
    public OWLDataFactory df();
    public OWLOntologyManager manager();
    public OWLReasoner reasoner();
    public OWLOntology ontology();
    public String expandNamespace(String value);
}

interface IPluginContext {
    public IOntologyContext ontologyContext(String ontolocation);
}

public class OWLAPIPlugin implements IPlugin, IPluginContext {
    private static final Logger LOGGER = LogManager.getLogger("Hexlite-OWLAPIPlugin");

    private class OntologyContext implements IOntologyContext {
        String _uri;
        JSONObject _namespaces;
        OWLDataFactory _df;
        OWLOntologyManager _manager;
        OWLOntology _ontology;
        OWLReasonerFactory _reasonerFactory;
        OWLReasoner _reasoner;

        private String extendURI(final String uri) {
            if( uri.indexOf("://") == -1 ) {
                if( uri.startsWith("/") ) {
                    return "file://"+uri;
                } else {
                    return "file://" + System.getProperty("user.dir") + "/" + uri;
                }
            } else {
                return uri;
            }
        }

        private JSONObject loadMetaFile(final String metafile) {
            final JSONParser parser = new JSONParser();

            try {
                return (JSONObject)parser.parse(new FileReader(metafile));
            } catch(final IOException io) {
                LOGGER.error("error loading JSON from "+metafile, io);
            } catch(final ParseException pe) {
                LOGGER.error("error loading JSON from "+metafile, pe);
            }
            return null;
        }

        public OntologyContext(final String metafile) {
            final JSONObject meta = loadMetaFile(metafile);

            _uri = extendURI((String)meta.get("load-uri"));
            if(meta.containsKey("namespaces")) {
                _namespaces = (JSONObject)meta.get("namespaces");
            } else {
                _namespaces = new JSONObject();
            }
            _df = OWLManager.getOWLDataFactory();
            _manager = OWLManager.createOWLOntologyManager();
            
            // make dependency ontologies auto-loadable from current directory
            final File file = new File(System.getProperty("user.dir"));
            _manager.getIRIMappers().add(new AutoIRIMapper(file, true));

            try {
                _ontology = _manager.loadOntology(IRI.create(_uri));
            } catch(final OWLOntologyCreationException e) {
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
        public String namespace(final String key) { return (String)_namespaces.get(key); }
        public String expandNamespace(final String value) {
            final int idx = value.indexOf(":");
            if(idx == -1 || (value.length() > idx && value.charAt(idx+1) == '/') ) {
                // no namespace
                return value;
            } else {
                final String prefix = value.substring(0,idx);
                final String suffix = value.substring(idx+1);
                LOGGER.debug("expandNamespace got prefix "+prefix+" and suffix "+suffix);
                String ret = value;
                if(_namespaces.containsKey(prefix)) {
                    ret = namespace(prefix)+suffix;
                } else {
                    LOGGER.warn("encountered unknown prefix "+prefix);
                }
                LOGGER.debug("expandNamespace changed "+value+" to "+ret);
                return ret;
            }
        }
    }

    private final AbstractMap<String, IOntologyContext> cachedContexts;

    public OWLAPIPlugin() {
        cachedContexts = new HashMap<String, IOntologyContext>();
    }

    @Override
    public IOntologyContext ontologyContext(final String ontolocation) {
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

        private String withoutQuotes(final String s) {
            if( s.startsWith("\"") && s.endsWith("\"") )
                return s.substring(1, s.length()-1);
            else
                return s;
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            LOGGER.debug("retrieve of {}", () -> getPredicate());
            final String location = withoutQuotes(query.getInput().get(0).value());
            final String conceptQuery = withoutQuotes(query.getInput().get(1).value());
            LOGGER.info("{} retrieving with ontoURI={} and query {}", () -> getPredicate(), () -> location, () -> conceptQuery);
            final IOntologyContext oc = ontologyContext(location);
            final String expandedConceptQuery = oc.expandNamespace(conceptQuery);
            LOGGER.debug("expanded query to {}", () -> expandedConceptQuery);

            final Answer answer = new Answer();
            final OWLClassExpression classquery = oc.df().getOWLClass(IRI.create(expandedConceptQuery));
            LOGGER.debug("querying ontology with expression {}", () -> classquery);
            oc.reasoner().getInstances(classquery, false).entities().forEach( instance -> {
                LOGGER.debug("found instance {}", () -> instance);
                final ArrayList<ISymbol> t = new ArrayList<ISymbol>(1);
                t.add(ctx.storeConstant(instance.getIRI().toString())); // maybe getShortForm()
                answer.output(t);
            });
            
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