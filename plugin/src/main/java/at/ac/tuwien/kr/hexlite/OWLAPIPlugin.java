package at.ac.tuwien.kr.hexlite;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLObjectProperty;

import at.ac.tuwien.kr.hexlite.api.Answer;
import at.ac.tuwien.kr.hexlite.api.ExtSourceProperties;
import at.ac.tuwien.kr.hexlite.api.IPlugin;
import at.ac.tuwien.kr.hexlite.api.IPluginAtom;
import at.ac.tuwien.kr.hexlite.api.ISolverContext;
import at.ac.tuwien.kr.hexlite.api.ISymbol;

public class OWLAPIPlugin implements IPlugin {
    private static final Logger LOGGER = LogManager.getLogger("Hexlite-OWLAPIPlugin");

    private final AbstractMap<String, IOntologyContext> cachedContexts;

    public OWLAPIPlugin() {
        cachedContexts = new HashMap<String, IOntologyContext>();
    }

    // @Override
    public IOntologyContext ontologyContext(final String ontolocation) {
        if (!cachedContexts.containsKey(ontolocation)) {
            cachedContexts.put(ontolocation, new OntologyContext(ontolocation));
        }
        return cachedContexts.get(ontolocation);
    }

    public static abstract class BaseAtom implements IPluginAtom {
        private final String predicate;
        private final ArrayList<InputType> inputArguments;
        private final int outputArguments;
        private final ExtSourceProperties properties;

        public BaseAtom(final String _predicate, final List<InputType> _extraArgumentTypes,
                final int _outputArguments) {
            // first argument = ontology meta file location
            predicate = _predicate;
            inputArguments = new ArrayList<InputType>();
            inputArguments.add(InputType.CONSTANT);
            for (final InputType arg : _extraArgumentTypes) {
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
            if (s.startsWith("\"") && s.endsWith("\""))
                return s.substring(1, s.length() - 1);
            else
                return s;
        }
    }

    public class ClassQueryReadOnlyAtom extends BaseAtom {
        public ClassQueryReadOnlyAtom() {
            super("dlCro", Arrays.asList(new InputType[] { InputType.CONSTANT }), 1);
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            LOGGER.debug("retrieve of {}", () -> getPredicate());
            final String location = withoutQuotes(query.getInput().get(0).value());
            final String conceptQuery = withoutQuotes(query.getInput().get(1).value());
            LOGGER.info("{} retrieving with ontoURI={} and query {}", () -> getPredicate(), () -> location,
                    () -> conceptQuery);
            final IOntologyContext oc = ontologyContext(location);
            final String expandedQuery = oc.expandNamespace(conceptQuery);
            LOGGER.debug("expanded query to {}", () -> expandedQuery);

            final Answer answer = new Answer();
            final OWLClassExpression owlquery = oc.df().getOWLClass(IRI.create(expandedQuery));
            LOGGER.debug("querying ontology with expression {}", () -> owlquery);
            oc.reasoner().getInstances(owlquery, false).entities().forEach(instance -> {
                LOGGER.debug("found instance {}", () -> instance);
                final ArrayList<ISymbol> t = new ArrayList<ISymbol>(1);
                t.add(ctx.storeConstant(instance.getIRI().toString())); // maybe getShortForm()
                answer.output(t);
            });

            return answer;
        }
    }

    public class ObjectPropertyReadOnlyQueryAtom extends BaseAtom {
        public ObjectPropertyReadOnlyQueryAtom() {
            super("dlOPro", Arrays.asList(new InputType[] { InputType.CONSTANT }), 2);
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            LOGGER.debug("retrieve of {}", () -> getPredicate());
            final String location = withoutQuotes(query.getInput().get(0).value());
            final String opQuery = withoutQuotes(query.getInput().get(1).value());
            LOGGER.info("{} retrieving with ontoURI={} and query {}", () -> getPredicate(), () -> location,
                    () -> opQuery);
            final IOntologyContext oc = ontologyContext(location);
            final String expandedQuery = oc.expandNamespace(opQuery);
            LOGGER.debug("expanded query to {}", () -> expandedQuery);

            final Answer answer = new Answer();
            final OWLObjectProperty op = oc.df().getOWLObjectProperty(IRI.create(expandedQuery));
            LOGGER.debug("querying ontology with expression {}", () -> op);
            oc.reasoner().objectPropertyDomains(op).flatMap(domainclass -> oc.reasoner().instances(domainclass, false))
                    .distinct().forEach(domainindividual -> {
                        oc.reasoner().objectPropertyValues(domainindividual, op).forEach(value -> {
                            LOGGER.debug("found individual {} related via {} to individual {}", () -> domainindividual,
                                    () -> op, () -> value);
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
        private static List<InputType> prepareArguments(final List<InputType> _extraArgumentTypes) {
            final ArrayList<InputType> ret = new ArrayList<InputType>();
            ret.add(InputType.PREDICATE);
            ret.add(InputType.CONSTANT);
            ret.addAll(_extraArgumentTypes);
            System.err.println("returning " + ret.toString());
            return ret;
        }

        public ModifiedOntologyBaseAtom(final String _predicate, final List<InputType> _extraArgumentTypes) {
            super(_predicate, prepareArguments(_extraArgumentTypes), 0);
            // first argument = ontology meta file location (from BaseAtom)
            // second argument = delta predicate
            // third argument = delta selector
            // remaining arguments = _extraArgumentTypes from superclass
            // no output arguments (=0)
        }

        public List<IOntologyModification> extractModifications(Set<? extends List<ISymbol>> delta_ext, ISymbol delta_sel) {
            final List<IOntologyModification> ret = new ArrayList<IOntologyModification>(10);
            ret.add(new OntologyContext.AddClassAssertionAxiomOntologyModification(
                IRI.create("koala", "Marsupial"),
                IRI.create("koalaex", "Silvia")
            ));
            return ret;
        }
    }

    public class ModifiedOntologyConsistentAtom extends ModifiedOntologyBaseAtom {
        public ModifiedOntologyConsistentAtom() {
            // dlConsistent[ontospec,deltapredicate,selector]
            // true iff the specified ontology after modification by
            //          the delta in deltapredicate selected by the selector
            //          is consistent
            super("dlConsistent", new ArrayList<InputType>());
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            final String location = withoutQuotes(query.getInput().get(0).value());
            LOGGER.info("{} retrieving with ontoURI={}", () -> getPredicate(), () -> location);
            final IOntologyContext oc = ontologyContext(location);
            final Set<? extends List<ISymbol>> delta_ext = query.getInput().get(1).extension();
            final ISymbol delta_sel = query.getInput().get(2);
            final List<? extends IOntologyModification> modifications = extractModifications(delta_ext, delta_sel);
            final IOntologyContext moc = oc.modifiedCopy(modifications);
            final Answer answer = new Answer();
            if( moc.reasoner().isConsistent() )
                answer.output(new ArrayList<ISymbol>());
            return answer;
        }
    }
    
    public class DataPropertyReadOnlyQueryAtom extends BaseAtom {
        public DataPropertyReadOnlyQueryAtom() {
            super("dlDPro", Arrays.asList(new InputType[] { InputType.CONSTANT }), 2);
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            LOGGER.debug("retrieve of {}", () -> getPredicate());
            final String location = withoutQuotes(query.getInput().get(0).value());
            final String dpQuery = withoutQuotes(query.getInput().get(1).value());
            LOGGER.info("{} retrieving with ontoURI={} and query {}", () -> getPredicate(), () -> location, () -> dpQuery);
            final IOntologyContext oc = ontologyContext(location);
            final String expandedQuery = oc.expandNamespace(dpQuery);
            LOGGER.debug("expanded query to {}", () -> expandedQuery);

            final Answer answer = new Answer();
            final OWLDataProperty dp = oc.df().getOWLDataProperty(IRI.create(expandedQuery));
            LOGGER.debug("querying ontology with expression {}", () -> dp);
            oc.reasoner().dataPropertyDomains(dp)
                .flatMap( domainclass -> oc.reasoner().instances(domainclass, false) )
                .distinct()
                .forEach( domainindividual -> {
                    oc.reasoner().dataPropertyValues(domainindividual, dp).forEach( value -> {
                        LOGGER.debug("found individual {} related via data property {} to value {}", () -> domainindividual, () -> dp, () -> value);
                        final ArrayList<ISymbol> t = new ArrayList<ISymbol>(2);
                        t.add(ctx.storeConstant(domainindividual.getIRI().toString())); // maybe getShortForm()
                        t.add(ctx.storeConstant(value.getLiteral())); // maybe deal with integers/types differently: value.isBoolean value.isInteger
                        answer.output(t);
                    });
                });
            
            return answer;
        }
    }

    public class SimplifyIRIAtom extends BaseAtom {
        public SimplifyIRIAtom() {
            super("dlSimplifyIRI", Arrays.asList(new InputType[] { InputType.CONSTANT }), 1);
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
        atoms.add(new ClassQueryReadOnlyAtom());
        atoms.add(new ObjectPropertyReadOnlyQueryAtom());
        atoms.add(new DataPropertyReadOnlyQueryAtom());
        atoms.add(new ModifiedOntologyConsistentAtom());
        atoms.add(new SimplifyIRIAtom());
        return atoms;        
	}
}