package at.ac.tuwien.kr.hexlite;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.json.simple.parser.JSONParser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
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
    public String simplifyNamespaceIfPossible(String value);
}

interface IPluginContext {
    public IOntologyContext ontologyContext(String ontolocation);
}

public class OWLAPIPlugin implements IPlugin, IPluginContext {
    private static final Logger LOGGER = LogManager.getLogger("Hexlite-OWLAPIPlugin");

    private static class OntologyContext implements IOntologyContext {
        String _uri;
        HashMap<String,String> _namespaces;
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
            _namespaces = new HashMap<String,String>();
            if(meta.containsKey("namespaces")) {
                final JSONObject nsobject = (JSONObject)meta.get("namespaces");
                for(final Object ok : nsobject.keySet()) {
                    if( ok instanceof String ) {
                        final String k = (String) ok;           
                        final Object ov = nsobject.get(ok);
                        if( ov instanceof String ) {
                            _namespaces.put(k, (String)ov);
                        } else {
                            LOGGER.error("namespaces must have String values, skipping {} / {}", () -> k, () -> ov);
                        }
                    } else {
                        LOGGER.error("namespaces must have String keys, skipping {}", () -> ok);
                    }
                }
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
        public String namespace(final String key) { return _namespaces.get(key); }
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
        
        public String simplifyNamespaceIfPossible(final String value) {
            for(final Map.Entry<String,String> entry : _namespaces.entrySet()) {
                if(value.startsWith(entry.getValue())) {
                    return entry.getKey() + ":" + value.substring(entry.getValue().length());
                }
            }
            return value;
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

    public static abstract class BaseAtom implements IPluginAtom {
        private final String predicate;
        private final ArrayList<InputType> inputArguments;
        private final int outputArguments;
        private final ExtSourceProperties properties;

        public BaseAtom(final String _predicate, final InputType[] _extraArgumentTypes, final int _outputArguments) {
            // first argument = ontology meta file location
            predicate = _predicate;
            inputArguments = new ArrayList<InputType>();
            inputArguments.add(InputType.CONSTANT);
            for(final InputType arg : _extraArgumentTypes) {
                inputArguments.add(arg);
            }
            outputArguments = _outputArguments;
            properties = new ExtSourceProperties();
        }

        @Override
        public String getPredicate() {
            return predicate;
        }

        @Override
        public ArrayList<InputType> getInputArguments() {
            return inputArguments;
        }

        @Override
        public int getOutputArguments() {
            return outputArguments;
        }

        @Override
        public ExtSourceProperties getExtSourceProperties() {
            return properties;
        }

        protected String withoutQuotes(final String s) {
            if( s.startsWith("\"") && s.endsWith("\"") )
                return s.substring(1, s.length()-1);
            else
                return s;
        }
    }

    public static class ConceptQueryAtom extends BaseAtom {
        public ConceptQueryAtom() {
            super("dlCro", new InputType[] { InputType.CONSTANT }, 1);
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            LOGGER.debug("retrieve of {}", () -> getPredicate());
            final String location = withoutQuotes(query.getInput().get(0).value());
            final String conceptQuery = withoutQuotes(query.getInput().get(1).value());
            LOGGER.info("{} retrieving with ontoURI={} and query {}", () -> getPredicate(), () -> location, () -> conceptQuery);
            final IOntologyContext oc = ontologyContext(location);
            final String expandedQuery = oc.expandNamespace(conceptQuery);
            LOGGER.debug("expanded query to {}", () -> expandedQuery);

            final Answer answer = new Answer();
            final OWLClassExpression owlquery = oc.df().getOWLClass(IRI.create(expandedQuery));
            LOGGER.debug("querying ontology with expression {}", () -> owlquery);
            oc.reasoner().getInstances(owlquery, false).entities().forEach( instance -> {
                LOGGER.debug("found instance {}", () -> instance);
                final ArrayList<ISymbol> t = new ArrayList<ISymbol>(1);
                t.add(ctx.storeConstant(instance.getIRI().toString())); // maybe getShortForm()
                answer.output(t);
            });
            
            return answer;
        }
    }

    public static class DataRoleQueryAtom extends BaseAtom {
        public DataRoleQueryAtom() {
            super("dlDRro", new InputType[] { InputType.CONSTANT }, 2);
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            LOGGER.debug("retrieve of {}", () -> getPredicate());
            final String location = withoutQuotes(query.getInput().get(0).value());
            final String dataRoleQuery = withoutQuotes(query.getInput().get(1).value());
            LOGGER.info("{} retrieving with ontoURI={} and query {}", () -> getPredicate(), () -> location, () -> dataRoleQuery);
            final IOntologyContext oc = ontologyContext(location);
            final String expandedQuery = oc.expandNamespace(dataRoleQuery);
            LOGGER.debug("expanded query to {}", () -> expandedQuery);

            final Answer answer = new Answer();
            final OWLObjectProperty op = oc.df().getOWLObjectProperty(IRI.create(expandedQuery));
            LOGGER.debug("querying ontology with expression {}", () -> op);
            oc.reasoner().objectPropertyDomains(op)
                .flatMap( domainclass -> oc.reasoner().instances(domainclass, false) )
                .distinct()
                .forEach( domainindividual -> {
                    oc.reasoner().objectPropertyValues(domainindividual, op).forEach( value -> {
                        LOGGER.debug("found individual {} related via {} to individual {}", () -> domainindividual, () -> op, () -> value);
                        final ArrayList<ISymbol> t = new ArrayList<ISymbol>(2);
                        t.add(ctx.storeConstant(domainindividual.getIRI().toString())); // maybe getShortForm()
                        t.add(ctx.storeConstant(value.getIRI().toString())); // maybe getShortForm()
                        answer.output(t);
                    });
                });
            
            return answer;
        }
    }

    public abstract static class ModifiedOntologyBaseAtom extends BaseAtom {
        private static InputType[] prepareArguments(final InputType[] _extraArgumentTypes) {
            final ArrayList<InputType> ret = new ArrayList<InputType>();
            ret.add(InputType.PREDICATE);
            ret.add(InputType.CONSTANT);
            for(final InputType arg : _extraArgumentTypes) {
                ret.add(arg);
            }
            return (InputType[])ret.toArray();
        }

        public ModifiedOntologyBaseAtom(final String _predicate, final InputType[] _extraArgumentTypes) {
            super(_predicate, prepareArguments(_extraArgumentTypes), 0);
            // first argument = ontology meta file location (from BaseAtom)
            // second argument = delta predicate
            // third argument = delta selector
            // remaining arguments = _extraArgumentTypes from superclass
            // no output arguments (=0)
        }
    }

    public class ModifiedOntologyConceptQueryAtom extends ModifiedOntologyBaseAtom {
        public ModifiedOntologyConceptQueryAtom() {
            // dlC[ontospec,deltapredicate,selector,concept,instance]
            // true iff concept(instance) is entailed by
            //          the specified ontology after modification by
            //          the delta in deltapredicate selected by the selector
            super("dlC", new InputType[] { InputType.CONSTANT, InputType.CONSTANT });
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            // TODO 
        }
    }

    public class SimplifyIRIAtom extends BaseAtom {
        public SimplifyIRIAtom() {
            super("dlSimplifyIRI", new InputType[] { InputType.CONSTANT }, 1);
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            LOGGER.debug("retrieve of {}", () -> getPredicate());
            final String location = withoutQuotes(query.getInput().get(0).value());
            final String iri = withoutQuotes(query.getInput().get(1).value());
            LOGGER.info("{} retrieving with ontoURI={} and query {}", () -> getPredicate(), () -> location, () -> iri);
            final IOntologyContext oc = ontologyContext(location);
            final String simplified = oc.simplifyNamespaceIfPossible(iri);
            LOGGER.debug("simplified to {}", () -> simplified);

            final Answer answer = new Answer();
            final ArrayList<ISymbol> t = new ArrayList<ISymbol>(1);
            t.add(ctx.storeConstant(simplified));
            answer.output(t);
            
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
        atoms.add(new ConceptQueryAtom());
        atoms.add(new DataRoleQueryAtom());
        atoms.add(new SimplifyIRIAtom());
        return atoms;        
	}
}